package io.github.m4gshm.reserve.service;

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
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass.*;
import reserve.v1.ReserveServiceGrpc;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.ExceptionUtils.newStatusRuntimeException;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.reserve.data.model.Reserve.Item;
import static io.github.m4gshm.reserve.data.model.Reserve.Status.*;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.*;
import static io.grpc.Status.FAILED_PRECONDITION;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;


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
    public void create(ReserveCreateRequest request, StreamObserver<ReserveCreateResponse> response) {
        grpc.subscribe(response, jooq.transactional(dsl -> {
            var paymentId = UUID.randomUUID().toString();
            var body = request.getBody();
            var items = body.getItemsList().stream().map(item -> Item.builder()
                            .id(item.getId())
                            .amount(item.getAmount())
                            .build())
                    .toList();
            var reserve = Reserve.builder()
                    .id(paymentId)
                    .externalRef(body.getExternalRef())
                    .status(created)
                    .items(items)
                    .build();
            var routine = reserveStorage.save(reserve)
                    .thenReturn(ReserveCreateResponse.newBuilder().setId(paymentId).build());
            return prepare(request.getTwoPhaseCommit(), dsl, reserve.id(), routine);
        }));
    }

    @Override
    public void approve(ReserveApproveRequest request, StreamObserver<ReserveApproveResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus(responseObserver, reserveId, Set.of(created), (dsl, reserve) -> {
            var items = reserve.items();
            var notReservedItems = items.stream().filter(item -> !item.reserved()).toList();

            if (notReservedItems.isEmpty()) {
                return error(newStatusRuntimeException(FAILED_PRECONDITION, "all items reserved already"));
            }

            var notReservedItemPerId = notReservedItems.stream().collect(toMap(Item::id, r -> r));

            return prepare(request.getTwoPhaseCommit(), dsl, reserveId, warehouseItemStorage.reserve(
                    toItemOps(notReservedItems)
            ).flatMap(reserveResults -> {
                var items1 = reserveResults.stream().map(itemReserveResult -> {
                    var itemId = itemReserveResult.id();
                    var item = requireNonNull(notReservedItemPerId.get(itemId), "no preloaded reserve item " + itemId);
                    var reserved = itemReserveResult.reserved();
                    return item.toBuilder()
                            .reserved(reserved)
                            .insufficient(!reserved ? -itemReserveResult.remainder() : null)
                            .build();
                }).toList();

                var updatingReserve = reserve.toBuilder();
                var allReserved = items1.size() == notReservedItems.size();
                if (allReserved) {
                    updatingReserve.status(approved);
                }
                var response = newApproveResponse(reserveResults, reserveId);
                return reserveStorage.save(updatingReserve
                        .items(items1)
                        .build()
                ).map(_ -> response).defaultIfEmpty(response);
            }));
        });
    }

    @Override
    public void release(ReserveReleaseRequest request, StreamObserver<ReserveReleaseResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus(responseObserver, reserveId, Set.of(approved), (dsl, reserve) -> {
            var items = toItemOps(reserve.items());
            return prepare(request.getTwoPhaseCommit(), dsl, reserveId, warehouseItemStorage.release(items)
                    .zipWith(reserveStorage.save(witStatus(reserve, released)), (i, r) -> {
                        log.debug("reserve released: id [{}], items: [{}]", r.id(), i.size());
                        return ReserveReleaseResponse.newBuilder()
                                .setId(reserveId)
                                .build();
                    }));
        });
    }

    @Override
    public void cancel(ReserveCancelRequest request, StreamObserver<ReserveCancelResponse> responseObserver) {
        var reserveId = request.getId();
        reserveInStatus(responseObserver, reserveId, Set.of(created, approved), (dsl, reserve) -> {
            var items = toItemOps(reserve.items());
            return prepare(request.getTwoPhaseCommit(), dsl, reserveId, warehouseItemStorage.cancelReserve(items)
                    .zipWith(reserveStorage.save(witStatus(reserve, cancelled)), (i, r) -> {
                        log.debug("reserve cancelled: id [{}], items: [{}]", r.id(), i.size());
                        return ReserveCancelResponse.newBuilder()
                                .setId(reserveId)
                                .build();
                    }));
        });
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

    private <T> void reserveInStatus(StreamObserver<T> responseObserver, String id, Set<Reserve.Status> expected,
                                     BiFunction<DSLContext, Reserve, Mono<? extends T>> routine) {
        grpc.subscribe(responseObserver, jooq.transactional(dsl -> reserveStorage.getById(id).flatMap(reserve -> {
            return checkStatus(reserve.status(), expected, just(reserve)).flatMap(_ -> {
                return routine.apply(dsl, reserve);
            });
        })));
    }
}

