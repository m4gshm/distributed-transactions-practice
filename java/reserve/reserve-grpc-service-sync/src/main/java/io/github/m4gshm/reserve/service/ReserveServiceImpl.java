package io.github.m4gshm.reserve.service;

import io.github.m4gshm.Grpc;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.reserve.data.model.ItemOp;
import io.github.m4gshm.reserve.data.model.Reserve;
import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reserve.data.access.jooq.enums.ReserveStatus;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceOuterClass;
import reserve.v1.ReserveServiceOuterClass.ReserveApproveRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveCancelRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveCancelResponse;
import reserve.v1.ReserveServiceOuterClass.ReserveCreateRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveCreateResponse;
import reserve.v1.ReserveServiceOuterClass.ReserveGetRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveGetResponse;
import reserve.v1.ReserveServiceOuterClass.ReserveListRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveListResponse;
import reserve.v1.ReserveServiceOuterClass.ReserveReleaseRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveReleaseResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.ExceptionUtils.newValidateException;
import static io.github.m4gshm.protobuf.Utils.getOrNull;
import static io.github.m4gshm.reserve.data.model.Reserve.Item;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.newApproveResponse;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.toItemOps;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.toReserveProto;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.toStatusProto;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static reserve.data.access.jooq.enums.ReserveStatus.APPROVED;
import static reserve.data.access.jooq.enums.ReserveStatus.CANCELLED;
import static reserve.data.access.jooq.enums.ReserveStatus.CREATED;
import static reserve.data.access.jooq.enums.ReserveStatus.RELEASED;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReserveServiceImpl extends ReserveServiceGrpc.ReserveServiceImplBase {
    io.github.m4gshm.reserve.data.ReserveStorage reserveStorage;
    io.github.m4gshm.reserve.data.WarehouseItemStorage warehouseItemStorage;
    PreparedTransactionService preparedTransactionService;
    Grpc grpc;

    private static Reserve witStatus(Reserve reserve, ReserveStatus status) {
        return reserve.toBuilder().status(status).build();
    }

    @Override
    public void approve(ReserveApproveRequest request,
                        StreamObserver<ReserveServiceOuterClass.ReserveApproveResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus("release", responseObserver, reserveId, Set.of(CREATED), reserve -> {
            var items = reserve.items();
            var notReservedItems = items.stream().filter(item -> !item.reserved()).toList();

            if (notReservedItems.isEmpty()) {
                throw newValidateException("all items reserved already");
            }

            var notReservedItemPerId = notReservedItems.stream().collect(toMap(Item::id, r -> r));

            var preparedTransactionId = getOrNull(request,
                    r -> r.hasPreparedTransactionId(),
                    r -> r.getPreparedTransactionId());
            if (preparedTransactionId != null) {
                preparedTransactionService.prepare(preparedTransactionId);
            }

            List<ItemOp.ReserveResult> reserveResults = warehouseItemStorage.reserve(toItemOps(notReservedItems));
            var reservedItems = reserveResults.stream().map(itemReserveResult -> {
                var itemId = itemReserveResult.id();
                var item = requireNonNull(notReservedItemPerId.get(itemId),
                        "no preloaded reserve item "
                                + itemId);
                var reserved = itemReserveResult.reserved();
                return item.toBuilder()
                        .reserved(reserved)
                        .insufficient(!reserved
                                ? -itemReserveResult.remainder()
                                : null)
                        .build();
            }).toList();

            var updatingReserve = reserve.toBuilder();
            var allReserved = reservedItems.size() == notReservedItems.size();
            if (allReserved) {
                updatingReserve.status(APPROVED);
            }
            var response = newApproveResponse(reserveResults, reserveId);
            reserveStorage.save(updatingReserve
                    .items(reservedItems)
                    .build());
            return response;
        });
    }

    @Override
    public void cancel(ReserveCancelRequest request, StreamObserver<ReserveCancelResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus("cancel", responseObserver, reserveId, Set.of(CREATED, APPROVED), reserve -> {
            var items = toItemOps(reserve.items());

            var preparedTransactionId = getOrNull(request,
                    r -> r.hasPreparedTransactionId(),
                    r -> r.getPreparedTransactionId());
            if (preparedTransactionId != null) {
                preparedTransactionService.prepare(preparedTransactionId);
            }

            var i = warehouseItemStorage.cancelReserve(items);
            var r = reserveStorage.save(witStatus(reserve, CANCELLED));

            log.debug("reserve cancelled: id [{}], items: [{}]",
                    r.id(),
                    i.size());
            return ReserveCancelResponse.newBuilder()
                    .setId(reserveId)
                    .setStatus(toStatusProto(r.status()))
                    .build();

        });
    }

    @Override
    public void create(ReserveCreateRequest request, StreamObserver<ReserveCreateResponse> response) {
        grpc.subscribe("create", response, () -> {
            var paymentId = UUID.randomUUID().toString();
            var body = request.getBody();

            var items = body.getItemsList()
                    .stream()
                    .map(item -> Item.builder()
                            .id(item.getId())
                            .amount(item.getAmount())
                            .build())
                    .toList();
            var reserve = Reserve.builder()
                    .id(paymentId)
                    .externalRef(body.getExternalRef())
                    .status(CREATED)
                    .items(items)
                    .build();

            var preparedTransactionId = getOrNull(request,
                    r -> r.hasPreparedTransactionId(),
                    r -> r.getPreparedTransactionId());
            if (preparedTransactionId != null) {
                preparedTransactionService.prepare(preparedTransactionId);
            }

            reserveStorage.save(reserve);
            return ReserveCreateResponse.newBuilder().setId(paymentId).build();
        });
    }

    @Override
    public void get(ReserveGetRequest request, StreamObserver<ReserveGetResponse> responseObserver) {
        grpc.subscribe("get", responseObserver, () -> {
            var reserve = reserveStorage.getById(request.getId());
            return ReserveGetResponse.newBuilder().setReserve(toReserveProto(reserve)).build();
        });
    }

    @Override
    public void list(ReserveListRequest request, StreamObserver<ReserveListResponse> responseObserver) {
        grpc.subscribe("list", responseObserver, () -> {
            var reserves = reserveStorage.findAll();
            return ReserveListResponse.newBuilder()
                    .addAllReserves(reserves.stream().map(ReserveServiceUtils::toReserveProto).toList())
                    .build();
        });
    }

    @Override
    public void release(ReserveReleaseRequest request, StreamObserver<ReserveReleaseResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus("release", responseObserver, reserveId, Set.of(APPROVED), reserve -> {
            var items = toItemOps(reserve.items());
            var preparedTransactionId = getOrNull(request,
                    r -> r.hasPreparedTransactionId(),
                    r -> r.getPreparedTransactionId());
            if (preparedTransactionId != null) {
                preparedTransactionService.prepare(preparedTransactionId);
            }
            var i = warehouseItemStorage.release(items);
            var r = reserveStorage.save(witStatus(reserve, RELEASED));
            log.debug("reserve released: id [{}], items: [{}]",
                    r.id(),
                    i.size());
            return ReserveReleaseResponse.newBuilder()
                    .setId(reserveId)
                    .setStatus(toStatusProto(r.status()))
                    .build();
        });
    }

    private <T> void reserveInStatus(String opName,
                                     StreamObserver<T> responseObserver,
                                     String id,
                                     Set<ReserveStatus> expected,
                                     Function<Reserve, T> routine) {
        grpc.subscribe(opName, responseObserver, () -> {
            var reserve = reserveStorage.getById(id);
            checkStatus(opName, "reserve", id, reserve.status(), expected, null);
            return routine.apply(reserve);
        });
    }
}
