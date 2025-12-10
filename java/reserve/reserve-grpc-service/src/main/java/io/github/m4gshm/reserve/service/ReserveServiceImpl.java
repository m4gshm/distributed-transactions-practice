package io.github.m4gshm.reserve.service;

import io.github.m4gshm.LogUtils;
import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.reactive.GrpcReactive;
import io.github.m4gshm.reserve.data.ReserveStorage;
import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.github.m4gshm.reserve.data.model.Reserve;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.context.Context;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
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

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.ExceptionUtils.newValidateException;
import static io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.protobuf.Utils.getOrNull;
import static io.github.m4gshm.reserve.data.model.Reserve.Item;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.newApproveResponse;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.toItemOps;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.toReserveProto;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.toStatusProto;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static reactor.core.publisher.Mono.error;
import static reserve.data.access.jooq.enums.ReserveStatus.APPROVED;
import static reserve.data.access.jooq.enums.ReserveStatus.CANCELLED;
import static reserve.data.access.jooq.enums.ReserveStatus.CREATED;
import static reserve.data.access.jooq.enums.ReserveStatus.RELEASED;

;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReserveServiceImpl extends ReserveServiceGrpc.ReserveServiceImplBase {
    ReserveStorage reserveStorage;

    WarehouseItemStorage warehouseItemStorage;
    Jooq jooq;
    GrpcReactive grpc;

    private static Reserve witStatus(Reserve reserve, ReserveStatus status) {
        return reserve.toBuilder().status(status).build();
    }

    @Override
    public void approve(ReserveApproveRequest request,
                        StreamObserver<ReserveServiceOuterClass.ReserveApproveResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus("release", responseObserver, reserveId, Set.of(CREATED), (dsl, reserve) -> {
            var items = reserve.items();
            var notReservedItems = items.stream().filter(item -> !item.reserved()).toList();

            if (notReservedItems.isEmpty()) {
                return error(newValidateException("all items reserved already"));
            }

            var notReservedItemPerId = notReservedItems.stream().collect(toMap(Item::id, r -> r));

            return prepare(dsl,
                    getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                    warehouseItemStorage.reserve(toItemOps(notReservedItems)).flatMap(reserveResults -> {
                        var items1 = reserveResults.stream()
                                .map(itemReserveResult -> {
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
                                })
                                .toList();

                        var updatingReserve = reserve.toBuilder();
                        var allReserved = items1.size() == notReservedItems.size();
                        if (allReserved) {
                            updatingReserve.status(APPROVED);
                        }
                        var response = newApproveResponse(reserveResults, reserveId);
                        return reserveStorage.save(updatingReserve
                                .items(items1)
                                .build())
                                .map(_ -> response)
                                .defaultIfEmpty(response);
                    }));
        });
    }

    @Override
    public void cancel(ReserveCancelRequest request, StreamObserver<ReserveCancelResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus("cancel", responseObserver, reserveId, Set.of(CREATED, APPROVED), (dsl, reserve) -> {
            var items = toItemOps(reserve.items());
            return prepare(dsl,
                    getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                    warehouseItemStorage.cancelReserve(items)
                            .zipWith(reserveStorage.save(witStatus(reserve, CANCELLED)), (i, r) -> {
                                log.debug("reserve cancelled: id [{}], items: [{}]",
                                        r.id(),
                                        i.size());
                                return ReserveCancelResponse.newBuilder()
                                        .setId(reserveId)
                                        .setStatus(toStatusProto(r.status()))
                                        .build();
                            }));
        });
    }

    @Override
    public void create(ReserveCreateRequest request, StreamObserver<ReserveCreateResponse> response) {
        grpc.subscribe("create", response, jooq.inTransaction(dsl -> {
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
            var routine = reserveStorage.save(reserve)
                    .thenReturn(ReserveCreateResponse.newBuilder().setId(paymentId).build());
            return prepare(dsl,
                    getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                    routine);
        }));
    }

    @Override
    public void get(ReserveGetRequest request, StreamObserver<ReserveGetResponse> responseObserver) {
        grpc.subscribe("get", responseObserver, reserveStorage.getById(request.getId()).map(reserve -> {
            return ReserveGetResponse.newBuilder().setReserve(toReserveProto(reserve)).build();
        }));
    }

    @Override
    public void list(ReserveListRequest request, StreamObserver<ReserveListResponse> responseObserver) {
        grpc.subscribe("list", responseObserver, reserveStorage.findAll().map(reserves -> {
            return ReserveListResponse.newBuilder()
                    .addAllReserves(reserves.stream().map(reserve -> toReserveProto(reserve)).toList())
                    .build();
        }));
    }

    private <T> Mono<T> log(String category, Mono<T> mono) {
        return LogUtils.log(getClass(), category, mono);
    }

    @Override
    public void release(ReserveReleaseRequest request, StreamObserver<ReserveReleaseResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus("release", responseObserver, reserveId, Set.of(APPROVED), (dsl, reserve) -> {
            var items = toItemOps(reserve.items());
            return prepare(dsl,
                    getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                    warehouseItemStorage.release(items)
                            .zipWith(reserveStorage.save(witStatus(reserve, RELEASED)),
                                    (i, r) -> {
                                        log.debug("reserve released: id [{}], items: [{}]",
                                                r.id(),
                                                i.size());
                                        return ReserveReleaseResponse.newBuilder()
                                                .setId(reserveId)
                                                .setStatus(toStatusProto(r.status()))
                                                .build();
                                    }));
        });
    }

    private <T> void reserveInStatus(String opName,
                                     StreamObserver<T> responseObserver,
                                     String id,
                                     Set<ReserveStatus> expected,
                                     BiFunction<DSLContext, Reserve, Mono<? extends T>> routine) {
        grpc.subscribe(opName,
                responseObserver,
                log(opName, jooq.inTransaction(dsl -> reserveStorage.getById(id).flatMap(reserve -> {
                    return checkStatus(opName, reserve.status(), expected, null).then(routine.apply(dsl, reserve));
                }))));
    }
}
