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
import org.springframework.stereotype.Service;
import reserve.v1.ReserveOuterClass.*;
import reserve.v1.ReserveServiceGrpc;

import java.util.UUID;

import static io.github.m4gshm.ExceptionUtils.newStatusException;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem.Result.Status.reserved;
import static io.github.m4gshm.reserve.data.model.Reserve.Item;
import static io.github.m4gshm.reserve.data.model.Reserve.Status.approved;
import static io.github.m4gshm.reserve.data.model.Reserve.Status.created;
import static io.github.m4gshm.reserve.service.ReserveServiceUtils.*;
import static io.grpc.Status.FAILED_PRECONDITION;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;
import static reactor.core.publisher.Mono.error;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReserveServiceImpl extends ReserveServiceGrpc.ReserveServiceImplBase {
    ReserveStorage reserveStorage;
    WarehouseItemStorage warehouseItemStorage;
    Jooq jooq;
    GrpcReactive grpc;

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
        grpc.subscribe(responseObserver, jooq.transactional(dsl -> reserveStorage.getById(request.getId()).flatMap(reserve -> {
            if (reserve.status() == approved) {
                return error(newStatusException(FAILED_PRECONDITION, "already approved"));
            }

            var items = reserve.items();
            var groupedByReserveStateItems = items.stream().collect(partitioningBy(item -> {
                return TRUE.equals(item.reserved());
            }));

            var notReservedItems = groupedByReserveStateItems.get(false);

            if (notReservedItems.isEmpty()) {
                return error(newStatusException(FAILED_PRECONDITION, "all items reserved already"));
            }

            var notReservedItemPerId = notReservedItems.stream().collect(toMap(Item::id, r -> r));

            var reserveId = reserve.id();
            return prepare(request.getTwoPhaseCommit(), dsl, reserve.id(), warehouseItemStorage.reserve(
                    toItemReserves(notReservedItems), reserveId
            ).flatMap(reserveResults -> {
                var successReserved = reserveResults.stream().filter(reserveResult -> {
                    return reserved.equals(reserveResult.status());
                }).map(itemReserveResult -> {
                    return requireNonNull(
                            notReservedItemPerId.get(itemReserveResult.id()),
                            "no preloaded reserve item " + itemReserveResult.id()
                    );
                }).map(i -> {
                    return i.toBuilder()
                            .reserved(true)
                            .build();
                }).toList();

                var updatingReserve = reserve.toBuilder();
                var allReserved = successReserved.size() == notReservedItems.size();
                if (allReserved) {
                    updatingReserve.status(approved);
                }
                var response = newApproveResponse(reserveResults, reserveId);
                return reserveStorage.save(updatingReserve
                        .items(successReserved)
                        .build()).map(_ -> response).defaultIfEmpty(response);
            }));
        })));
    }

    @Override
    public void cancel(ReserveCancelRequest request, StreamObserver<ReserveCancelResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void update(ReserveUpdateRequest request, StreamObserver<ReserveUpdateResponse> responseObserver) {
        super.update(request, responseObserver);
    }

    @Override
    public void list(ListReserveRequest request, StreamObserver<ListReserveResponse> responseObserver) {
        grpc.subscribe(responseObserver, reserveStorage.findAll().map(reserves -> {
            return ListReserveResponse.newBuilder()
                    .addAllReserves(reserves.stream().map(reserve -> toReserve(reserve)).toList())
                    .build();
        }));
    }

}
