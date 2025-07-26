package io.github.m4gshm.reserve.service;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.jooq.utils.TwoPhaseTransaction;
import io.github.m4gshm.reactive.GrpcReactive;
import io.github.m4gshm.reserve.data.ReserveStorage;
import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.github.m4gshm.reserve.data.model.Reserve;
import io.github.m4gshm.reserve.data.model.Reserve.Status;
import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveOuterClass.ListReserveRequest;
import reserve.v1.ReserveOuterClass.ListReserveResponse;
import reserve.v1.ReserveOuterClass.ReserveApproveRequest;
import reserve.v1.ReserveOuterClass.ReserveApproveResponse;
import reserve.v1.ReserveOuterClass.ReserveCancelRequest;
import reserve.v1.ReserveOuterClass.ReserveCancelResponse;
import reserve.v1.ReserveOuterClass.ReserveCreateRequest;
import reserve.v1.ReserveOuterClass.ReserveCreateResponse;
import reserve.v1.ReserveOuterClass.ReserveUpdateRequest;
import reserve.v1.ReserveOuterClass.ReserveUpdateResponse;
import reserve.v1.ReserveServiceGrpc;

import java.util.UUID;

import static io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem.Result.Status.RESERVED;
import static io.github.m4gshm.reserve.data.model.Reserve.Item;
import static io.github.m4gshm.reserve.service.ReserveServiceImplUtils.getItemStatus;
import static io.github.m4gshm.reserve.service.ReserveServiceImplUtils.newApproveResponse;
import static io.github.m4gshm.reserve.service.ReserveServiceImplUtils.toItemReserves;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

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
                    .status(Status.CREATED)
                    .items(items)
                    .build();
            var routine = reserveStorage.save(reserve)
                    .thenReturn(ReserveCreateResponse.newBuilder().setId(paymentId).build());
            return TwoPhaseTransaction.prepare(request.getTwoPhaseCommit(), dsl, reserve.id(), routine);
        }));
    }

    @Override
    public void approve(ReserveApproveRequest request, StreamObserver<ReserveApproveResponse> responseObserver) {
        grpc.subscribe(responseObserver, jooq.transactional(dsl -> reserveStorage.getById(request.getId()).flatMap(reserve -> {
            var twoPhaseCommit = request.getTwoPhaseCommit();
            //todo: must be in one transaction with twoPhaseCommit supporting

            var items = reserve.items();
            var notReservedItems = items.stream().filter(item -> TRUE.equals(item.reserved())).toList();
            var notReservedItemPerId = notReservedItems.stream().collect(toMap(Item::id, r -> r));

            var reserveId = reserve.id();
            var routine = warehouseItemStorage.reserve(
                    toItemReserves(notReservedItems), reserveId
            ).flatMap(reserveResults -> {
                var successReserved = reserveResults.stream()
                        .filter(reserveResult -> RESERVED.equals(reserveResult.status()))
                        .map(itemReserveResult -> requireNonNull(
                                notReservedItemPerId.get(itemReserveResult.id()),
                                "no preloaded reserve item " + itemReserveResult.id()
                        )).toList();

                var response = newApproveResponse(reserveResults, reserveId);
                return reserveStorage.saveReservedItems(reserveId, successReserved).map(l -> response).defaultIfEmpty(response);
            });
            return TwoPhaseTransaction.prepare(twoPhaseCommit, dsl, reserve.id(), routine);
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
                    .addAllReserves(reserves.stream().map(reserve -> ReserveOuterClass.Reserve.newBuilder()
                            .setId(reserve.id())
                            .setExternalRef(reserve.externalRef())
                            .addAllItems(reserve.items().stream().map(item -> ReserveOuterClass.Reserve.Item.newBuilder()
                                    .setId(item.id())
                                    .setAmount(item.amount())
                                    .setStatus(getItemStatus(item))
                                    .build()).toList())
                            .build()).toList())
                    .build();
        }));
    }
}
