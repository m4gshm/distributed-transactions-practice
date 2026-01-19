package io.github.m4gshm.payments.service;

import account.v1.AccountOuterClass;
import account.v1.AccountServiceGrpc.AccountServiceImplBase;
import account.v1.AccountServiceOuterClass;
import account.v1.AccountServiceOuterClass.AccountListRequest;
import account.v1.AccountServiceOuterClass.AccountListResponse;
import account.v1.AccountServiceOuterClass.AccountTopUpRequest;
import account.v1.AccountServiceOuterClass.AccountTopUpResponse;
import io.github.m4gshm.payments.data.ReactiveAccountStorage;
import io.github.m4gshm.payments.service.event.ReactiveAccountEventService;
import io.github.m4gshm.reactive.ReactiveGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.defer;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AccountServiceImpl extends AccountServiceImplBase {

    ReactiveGrpc grpc;
    ReactiveAccountStorage reactiveAccountStorage;
    ReactiveAccountEventService reactiveAccountEventService;

    @Override
    public void list(AccountListRequest request,
                     StreamObserver<AccountServiceOuterClass.AccountListResponse> responseObserver) {
        grpc.subscribe("list", responseObserver, () -> reactiveAccountStorage.findAll().map(accounts -> {
            return AccountListResponse.newBuilder().addAllAccounts(accounts.stream().map(account -> {
                return AccountOuterClass.Account.newBuilder()
                        .setClientId(account.clientId())
                        .setAmount(account.amount())
                        .setLocked(account.locked())
                        .mergeUpdatedAt(toTimestamp(account.updatedAt()))
                        .build();
            }).toList()).build();
        }));
    }

    @Override
    public void topUp(AccountTopUpRequest request, StreamObserver<AccountTopUpResponse> responseObserver) {
        grpc.subscribe("topUp", responseObserver, () -> defer(() -> {
            var topUp = request.getTopUp();
            var plus = topUp.getAmount();
            var clientId = topUp.getClientId();
            return reactiveAccountStorage.addAmount(clientId, plus).flatMap(result -> {
                return reactiveAccountEventService.sendAccountBalanceEvent(clientId,
                        result.balance(),
                        result.timestamp()
                ).doOnError(e -> {
                    log.error("event send error", e);
                }).thenReturn(AccountTopUpResponse.newBuilder().setBalance(result.balance()).build());
            });
        }));
    }
}
