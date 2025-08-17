package io.github.m4gshm.orders.service;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.jooq.utils.TwoPhaseTransaction;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.Orders;
import orders.v1.Orders.OrderCreateResponse;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import payment.v1.PaymentOuterClass;
import payment.v1.PaymentServiceGrpc;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.orders.data.model.Order.Status.approved;
import static io.github.m4gshm.orders.data.model.Order.Status.cancelled;
import static io.github.m4gshm.orders.data.model.Order.Status.created;
import static io.github.m4gshm.orders.data.model.Order.Status.insufficient;
import static io.github.m4gshm.orders.data.model.Order.Status.released;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.getOrderStatus;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.newCommitRequest;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.newRollbackRequest;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.notFoundById;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.string;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.toDelivery;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.toOrder;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.toOrderCreateResponse;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.toOrderStatus;
import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static lombok.AccessLevel.PROTECTED;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.fromSupplier;
import static reactor.core.publisher.Mono.just;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PROTECTED)
public class OrdersServiceImpl implements OrdersService {
    OrderStorage orderRepository;

    ReserveServiceGrpc.ReserveServiceStub reserveClient;

    TwoPhaseCommitServiceStub reserveClientTcp;
    PaymentServiceGrpc.PaymentServiceStub paymentsClient;
    TwoPhaseCommitServiceStub paymentsClientTcp;
    ItemService itemService;
    Jooq jooq;

    static Mono<Integer> localRollback(DSLContext dsl, String orderId, Throwable result) {
        return result instanceof TwoPhaseTransaction.PrepareTransactionException
                                                                                 ? TwoPhaseTransaction.rollback(dsl,
                                                                                                                string(orderId))
                                                                                 : Mono.<Integer>empty()
                                                                                       .doOnSubscribe(_ -> {
                                                                                           log.debug("no local prepared transaction for rollback");
                                                                                       });
    }

    public static Order orderWithStatus(Order order, Order.Status status) {
        return order.toBuilder()
                    .status(status)
                    .build();
    }

    @Override
    public Mono<Orders.OrderApproveResponse> approve(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp("approve", orderId, twoPhaseCommit, Set.of(created, insufficient), (payment, reserve) -> {
            return getOrderStatus(payment.getStatus(), reserve.getStatus());
        },
                             paymentId -> PaymentOuterClass.PaymentApproveRequest.newBuilder()
                                                                                 .setId(paymentId)
                                                                                 .setTwoPhaseCommit(twoPhaseCommit)
                                                                                 .build(),
                             reserveId -> ReserveOuterClass.ReserveApproveRequest.newBuilder()
                                                                                 .setId(reserveId)
                                                                                 .setTwoPhaseCommit(twoPhaseCommit)
                                                                                 .build(),
                             paymentsClient::approve,
                             reserveClient::approve,
                             order -> {
                                 return Orders.OrderApproveResponse.newBuilder()
                                                                   .setId(order.id())
                                                                   .setStatus(toOrderStatus(order.status()))
                                                                   .build();
                             });
    }

    @Override
    public Mono<Orders.OrderCancelResponse> cancel(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp("cancel",
                             orderId,
                             twoPhaseCommit,
                             Set.of(created, insufficient, approved),
                             (_,
                              _) -> cancelled,
                             paymentId -> PaymentOuterClass.PaymentCancelRequest.newBuilder()
                                                                                .setId(paymentId)
                                                                                .setTwoPhaseCommit(twoPhaseCommit)
                                                                                .build(),
                             reserveId -> ReserveOuterClass.ReserveCancelRequest.newBuilder()
                                                                                .setId(reserveId)
                                                                                .setTwoPhaseCommit(twoPhaseCommit)
                                                                                .build(),
                             paymentsClient::cancel,
                             reserveClient::cancel,
                             order -> {
                                 return Orders.OrderCancelResponse.newBuilder()
                                                                  .setId(order.id())
                                                                  .build();
                             });
    }

    @Override
    public Mono<OrderCreateResponse> create(Orders.OrderCreateRequest.OrderCreate createRequest,
                                            boolean twoPhaseCommit) {
        return fromSupplier(UUID::randomUUID).map(OrdersServiceUtils::string).flatMap(orderId -> {
            var itemsList = createRequest.getItemsList();
            var items = itemsList.stream().map(OrdersServiceUtils::toItem).toList();

            var itemIds = itemsList.stream().map(Orders.OrderCreateRequest.OrderCreate.Item::getId).distinct().toList();

            var paymentRoutine = itemService.getSumCost(itemIds).map(cost -> {
                return PaymentOuterClass.PaymentCreateRequest.newBuilder()
                                                             .setTwoPhaseCommit(twoPhaseCommit)
                                                             .setBody(PaymentOuterClass.PaymentCreateRequest.PaymentCreate.newBuilder()
                                                                                                                          .setExternalRef(orderId)
                                                                                                                          .setClientId(createRequest.getCustomerId())
                                                                                                                          .setAmount(cost)
                                                                                                                          .build())
                                                             .setTwoPhaseCommit(twoPhaseCommit)
                                                             .build();
            }).flatMap(paymentRequest -> {
                return toMono(paymentRequest, paymentsClient::create).onErrorResume(e -> {
                    log.error("error on {}", "payment::create", e);
                    return error(e);
                });
            });

            var reserveRequest = ReserveOuterClass.ReserveCreateRequest.newBuilder()
                                                                       .setTwoPhaseCommit(twoPhaseCommit)
                                                                       .setBody(ReserveOuterClass.ReserveCreateRequest.Reserve.newBuilder()
                                                                                                                              .setExternalRef(orderId)
                                                                                                                              .addAllItems(items.stream()
                                                                                                                                                .map(OrdersServiceUtils::toCreateReserveItem)
                                                                                                                                                .toList())
                                                                                                                              .build())
                                                                       .build();
            var reserveRoutine = toMono(reserveRequest, reserveClient::create);

            return paymentRoutine.zipWith(reserveRoutine).flatMap(responses -> {
                var paymentResponse = responses.getT1();
                var reserveResponse = responses.getT2();
                var paymentId = paymentResponse.getId();
                var reserveId = reserveResponse.getId();
                var order = Order.builder()
                                 .id(string(orderId))
                                 .status(created)
                                 .paymentId(paymentId)
                                 .reserveId(reserveId)
                                 .customerId(createRequest.getCustomerId())
                                 .delivery(toDelivery(createRequest.getDelivery()))
                                 .items(items)
                                 .build();
                return saveAllAndCommit(twoPhaseCommit, order, paymentId, reserveId);
            });
        }).map(order -> {
            return toOrderCreateResponse(order);
        });
    }

    private <T> Mono<T> distributedCommit(DSLContext dsl,
                                          String orderId,
                                          String paymentTransactionId,
                                          String reserveTransactionId,
                                          T result) {
        return toMono(newCommitRequest(reserveTransactionId), reserveClientTcp::commit)
                                                                                       .zipWith(toMono(newCommitRequest(paymentTransactionId),
                                                                                                       paymentsClientTcp::commit))
                                                                                       .then(TwoPhaseTransaction.commit(dsl,
                                                                                                                        string(orderId)))
                                                                                       .thenReturn(result)
                                                                                       .doOnSuccess(_ -> {
                                                                                           log.debug("distributed transaction commit for order is successful, orderId [{}]",
                                                                                                     orderId);
                                                                                       })
                                                                                       .doOnError(throwable -> {
                                                                                           log.error("distributed transaction commit for order is failed, orderId [{}]",
                                                                                                     orderId,
                                                                                                     throwable);
                                                                                       });
    }

    private <T> Mono<T> distributedRollback(DSLContext dsl,
                                            String orderId,
                                            String paymentTransactionId,
                                            String reserveTransactionId,
                                            Throwable result) {
        return remoteRollback(paymentTransactionId, reserveTransactionId)
                                                                         .then(localRollback(dsl, orderId, result))
                                                                         .then(defer(() -> {
                                                                             // todo need check actuality
                                                                             return Mono.<T>error(result);
                                                                         }))
                                                                         .switchIfEmpty(error(result))
                                                                         .doOnSubscribe(_ -> {
                                                                             log.debug("distributed transaction will on rollback, orderId [{}]",
                                                                                       orderId);
                                                                         })
                                                                         .doOnSuccess(_ -> {
                                                                             log.debug("distributed transaction rollback for order is successful, orderId [{}]",
                                                                                       orderId);
                                                                         })
                                                                         .doOnError(e -> {
                                                                             log.error("distributed transaction rollback for order is failed, orderId [{}]",
                                                                                       orderId,
                                                                                       e);
                                                                         });
    }

    @Override
    public Mono<Orders.OrderGetResponse> get(String orderId) {
        return orderRepository.findById(orderId).switchIfEmpty(notFoundById(orderId)).flatMap(order -> {
            return getItems(order.reserveId()).zipWith(getPaymentStatus(order.paymentId()), (items, paymentStatus) -> {
                return toOrder(order, paymentStatus, items);
            });
        }).map(order -> Orders.OrderGetResponse.newBuilder().setOrder(order).build());
    }

    private Mono<List<ReserveOuterClass.Reserve.Item>> getItems(String reserveId) {
        return toMono(ReserveOuterClass.ReserveGetRequest.newBuilder()
                                                         .setId(reserveId)
                                                         .build(),
                      reserveClient::get).map(reserveGetResponse -> {
                          return reserveGetResponse.getReserve().getItemsList();
                      });
    }

    private Mono<PaymentOuterClass.Payment.Status> getPaymentStatus(String paymentId) {
        return toMono(PaymentOuterClass.PaymentGetRequest.newBuilder()
                                                         .setId(paymentId)
                                                         .build(),
                      paymentsClient::get).map(response -> {
                          return response.getPayment().getStatus();
                      });
    }

    @Override
    public Mono<Orders.OrderListResponse> list() {
        return orderRepository.findAll().defaultIfEmpty(List.of()).map(orders -> {
            return orders.stream()
                         .map(order -> toOrder(order, null, null))
                         .toList();
        }).map(orders -> {
            return Orders.OrderListResponse.newBuilder()
                                           .addAllOrders(orders)
                                           .build();
        });
    }

    @Override
    public Mono<Orders.OrderReleaseResponse> release(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp("release",
                             orderId,
                             twoPhaseCommit,
                             Set.of(approved),
                             (_,
                              _) -> released,
                             paymentId -> PaymentOuterClass.PaymentPayRequest.newBuilder()
                                                                             .setId(paymentId)
                                                                             .setTwoPhaseCommit(twoPhaseCommit)
                                                                             .build(),
                             reserveId -> ReserveOuterClass.ReserveReleaseRequest.newBuilder()
                                                                                 .setId(reserveId)
                                                                                 .setTwoPhaseCommit(twoPhaseCommit)
                                                                                 .build(),
                             paymentsClient::pay,
                             reserveClient::release,
                             order -> {
                                 return Orders.OrderReleaseResponse.newBuilder()
                                                                   .setId(order.id())
                                                                   .build();
                             });
    }

    protected Mono<Void> remoteRollback(String paymentTransactionId, String reserveTransactionId) {
        return toMono(newRollbackRequest(reserveTransactionId), reserveClientTcp::rollback)
                                                                                           .zipWith(toMono(newRollbackRequest(paymentTransactionId),
                                                                                                           paymentsClientTcp::rollback))
                                                                                           .onErrorComplete(e -> {
                                                                                               log.warn("remote rollback error",
                                                                                                        e);
                                                                                               return true;
                                                                                           })
                                                                                           .then();
    }

    protected Mono<Order> saveAllAndCommit(boolean twoPhaseCommit,
                                           Order order,
                                           String paymentTransactionId,
                                           String reserveTransactionId) {
        var orderId = order.id();
        return jooq.transactional(dsl -> {
            // run distributed transaction
            return prepare(twoPhaseCommit, dsl, order.id(), orderRepository.save(order)).onErrorResume(throwable -> {
                // rollback distributed transaction on error
                log.error("error on transactional operation with orderId [{}]", orderId, throwable);
                return !twoPhaseCommit
                                       ? error(throwable)
                                       : distributedRollback(dsl,
                                                             orderId,
                                                             paymentTransactionId,
                                                             reserveTransactionId,
                                                             throwable);
            }).flatMap(savedOrder -> {
                // commit distributed transaction if no errors
                return !twoPhaseCommit
                                       ? just(savedOrder)
                                       : distributedCommit(dsl,
                                                           orderId,
                                                           paymentTransactionId,
                                                           reserveTransactionId,
                                                           savedOrder)
                                                                      .doOnError(throwable -> {
                                                                          log.error("error on commit distributed transaction [{}]",
                                                                                    orderId,
                                                                                    throwable);
                                                                      });
            });
        });
    }

    protected <T, PI, PO, RI, RO> Mono<T> updateOrderOp(String name,
                                                        String orderId,
                                                        boolean twoPhaseCommit,
                                                        Set<Order.Status> expected,
                                                        BiFunction<PO, RO, Order.Status> finalStatus,
                                                        Function<String, PI> paymentRequest,
                                                        Function<String, RI> reserveRequest,
                                                        BiConsumer<PI, StreamObserver<PO>> paymentOp,
                                                        BiConsumer<RI, StreamObserver<RO>> reserveOp,
                                                        Function<Order, T> responseBuilder) {
        return orderRepository.getById(orderId).flatMap(order -> {
            return checkStatus(order.status(), expected).then(defer(() -> {
                var paymentId = order.paymentId();
                var reserveId = order.reserveId();
                var paymentProcessRequest = paymentRequest.apply(paymentId);
                var reserveReleaseRequest = reserveRequest.apply(reserveId);
                return toMono(paymentProcessRequest, paymentOp).zipWith(toMono(reserveReleaseRequest, reserveOp),
                                                                        (po,
                                                                         ro) -> {
                                                                            log.debug("payment op '{}' result [{}] ",
                                                                                      name,
                                                                                      po);
                                                                            log.debug("reserve op '{}' result [{}] ",
                                                                                      name,
                                                                                      ro);
                                                                            log.info("order {} [{}]", name, orderId);
                                                                            return finalStatus.apply(po, ro);
                                                                        })
                                                               .onErrorResume(e -> {
                                                                   return twoPhaseCommit ? remoteRollback(paymentId,
                                                                                                          reserveId).then(error(e))
                                                                                         : error(e);
                                                               })
                                                               .flatMap(status -> {
                                                                   var orderWithNewStatus = orderWithStatus(order,
                                                                                                            status);
                                                                   if (status == insufficient) {
                                                                       log.info("abort op '{}' on insufficient status of orderId [{}]",
                                                                                name,
                                                                                orderId);
                                                                       return orderRepository.save(orderWithNewStatus)
                                                                                             .flatMap(savedOrder -> {
                                                                                                 return twoPhaseCommit ? remoteRollback(paymentId,
                                                                                                                                        reserveId)
                                                                                                                                                  .thenReturn(savedOrder)
                                                                                                                       : just(savedOrder);
                                                                                             });
                                                                   } else {
                                                                       return saveAllAndCommit(twoPhaseCommit,
                                                                                               orderWithNewStatus,
                                                                                               paymentId,
                                                                                               reserveId);
                                                                   }
                                                               })
                                                               .map(responseBuilder);
            }));
        });
    }

}
