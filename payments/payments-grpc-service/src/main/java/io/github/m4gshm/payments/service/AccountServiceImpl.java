package io.github.m4gshm.payments.service;

import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.service.integration.AccountEventService;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import payment.v1.AccountOuterClass;
import payment.v1.AccountOuterClass.AccountListRequest;
import payment.v1.AccountOuterClass.AccountListResponse;
import payment.v1.AccountOuterClass.AccountTopUpRequest;
import payment.v1.AccountOuterClass.AccountTopUpResponse;
import payment.v1.AccountServiceGrpc;
import reactor.core.publisher.Mono;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.just;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AccountServiceImpl extends AccountServiceGrpc.AccountServiceImplBase {

    GrpcReactive grpc;
    AccountStorage accountStorage;
    AccountEventService accountEventService;

    @Override
    public void list(AccountListRequest request, StreamObserver<AccountListResponse> responseObserver) {
        grpc.subscribe(responseObserver, accountStorage.findAll().map(accounts -> {
            return AccountListResponse.newBuilder().addAllAccounts(accounts.stream().map(account -> {
                return AccountOuterClass.Account.newBuilder()
                        .setClientId(account.clientId())
                        .setAmount(account.amount())
                        .setLocked(account.locked())
                        .setUpdatedAt(toTimestamp(account.updatedAt()))
                        .build();
            }).toList()).build();
        }));
    }

    @Override
    public void topUp(AccountTopUpRequest request, StreamObserver<AccountTopUpResponse> responseObserver) {
        grpc.subscribe(responseObserver, Mono.defer(() -> {
            var topUp = request.getTopUp();
            var plus = topUp.getAmount();
            var clientId = topUp.getClientId();
            return accountStorage.topUp(clientId, plus).flatMap(result -> {
                return accountEventService.sendAccountTopUp(clientId).doOnError(e -> {
                    log.error("event send error", e);
                }).thenReturn(AccountTopUpResponse.newBuilder()
                        .setBalance(result.balance())
                        .build());
            });
        }));
    }
}
