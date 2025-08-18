package io.github.m4gshm.reserve.service;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.ExceptionUtils.newStatusException;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.reserve.data.model.Reserve.Item;
import static io.github.m4gshm.reserve.data.model.Reserve.Status.APPROVED;
import static io.github.m4gshm.reserve.data.model.Reserve.Status.CANCELLED;
import static io.github.m4gshm.reserve.data.model.Reserve.Status.CREATED;
import static io.github.m4gshm.reserve.data.model.Reserve.Status.RELEASED;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.newApproveResponse;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.toItemOps;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.toReserve;
import static io.grpc.Status.FAILED_PRECONDITION;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static reactor.core.publisher.Mono.error;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.reactive.GrpcReactive;
import io.github.m4gshm.reserve.data.ReserveStorage;
import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.github.m4gshm.reserve.data.model.Reserve;
import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass.ReserveApproveRequest;
import reserve.v1.ReserveOuterClass.ReserveApproveResponse;
import reserve.v1.ReserveOuterClass.ReserveCancelRequest;
import reserve.v1.ReserveOuterClass.ReserveCancelResponse;
import reserve.v1.ReserveOuterClass.ReserveCreateRequest;
import reserve.v1.ReserveOuterClass.ReserveCreateResponse;
import reserve.v1.ReserveOuterClass.ReserveGetRequest;
import reserve.v1.ReserveOuterClass.ReserveGetResponse;
import reserve.v1.ReserveOuterClass.ReserveListRequest;
import reserve.v1.ReserveOuterClass.ReserveListResponse;
import reserve.v1.ReserveOuterClass.ReserveReleaseRequest;
import reserve.v1.ReserveOuterClass.ReserveReleaseResponse;
import reserve.v1.ReserveServiceGrpc;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReserveServiceImpl extends ReserveServiceGrpc.ReserveServiceImplBase {
    ReserveStorage reserveStorage;

    WarehouseItemStorage warehouseItemStorage;
    Jooq jooq;
    GrpcReactive grpc;

    private static Reserve witStatus(Reserve reserve, Reserve.Status status) {
        return reserve.toBuilder().status(status).build();
    }

    @Override
    public void approve(ReserveApproveRequest request, StreamObserver<ReserveApproveResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus(responseObserver, reserveId, Set.of(CREATED), (dsl, reserve) -> {
            var items = reserve.items();
            var notReservedItems = items.stream().filter(item -> !item.reserved()).toList();

            if (notReservedItems.isEmpty()) {
                return error(newStatusException(FAILED_PRECONDITION, "all items reserved already"));
            }

            var notReservedItemPerId = notReservedItems.stream().collect(toMap(Item::id, r -> r));

            return prepare(dsl,
                    request.getPreparedTransactionId(),
                    warehouseItemStorage.reserve(toItemOps(notReservedItems))
                            .flatMap(reserveResults -> {
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
        reserveInStatus(responseObserver, reserveId, Set.of(CREATED, APPROVED), (dsl, reserve) -> {
            var items = toItemOps(reserve.items());
            return prepare(dsl,
                    request.getPreparedTransactionId(),
                    warehouseItemStorage.cancelReserve(items)
                            .zipWith(reserveStorage.save(witStatus(reserve, CANCELLED)),
                                    (i, r) -> {
                                        log.debug("reserve cancelled: id [{}], items: [{}]",
                                                r.id(),
                                                i.size());
                                        return ReserveCancelResponse.newBuilder()
                                                .setId(reserveId)
                                                .build();
                                    }));
        });
    }

    @Override
    public void create(ReserveCreateRequest request, StreamObserver<ReserveCreateResponse> response) {
        grpc.subscribe(response, jooq.inTransaction(dsl -> {
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
            return prepare(dsl, request.getPreparedTransactionId(), routine);
        }));
    }

    @Override
    public void get(ReserveGetRequest request, StreamObserver<ReserveGetResponse> responseObserver) {
        grpc.subscribe(responseObserver, reserveStorage.getById(request.getId()).map(reserve -> {
            return ReserveGetResponse.newBuilder().setReserve(toReserve(reserve)).build();
        }));
    }

    @Override
    public void list(ReserveListRequest request, StreamObserver<ReserveListResponse> responseObserver) {
        grpc.subscribe(responseObserver, reserveStorage.findAll().map(reserves -> {
            return ReserveListResponse.newBuilder()
                    .addAllReserves(reserves.stream().map(reserve -> toReserve(reserve)).toList())
                    .build();
        }));
    }

    @Override
    public void release(ReserveReleaseRequest request, StreamObserver<ReserveReleaseResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus(responseObserver, reserveId, Set.of(APPROVED), (dsl, reserve) -> {
            var items = toItemOps(reserve.items());
            return prepare(dsl,
                    request.getPreparedTransactionId(),
                    warehouseItemStorage.release(items)
                            .zipWith(reserveStorage.save(witStatus(reserve, RELEASED)),
                                    (i,
                                     r) -> {
                                        log.debug("reserve released: id [{}], items: [{}]",
                                                r.id(),
                                                i.size());
                                        return ReserveReleaseResponse.newBuilder()
                                                .setId(reserveId)
                                                .build();
                                    }));
        });
    }

    private <T> void reserveInStatus(StreamObserver<T> responseObserver,
                                     String id,
                                     Set<Reserve.Status> expected,
                                     BiFunction<DSLContext, Reserve, Mono<? extends T>> routine) {
        grpc.subscribe(responseObserver, jooq.inTransaction(dsl -> reserveStorage.getById(id).flatMap(reserve -> {
            return checkStatus(reserve.status(), expected).then(routine.apply(dsl, reserve));
        })));
    }
}
