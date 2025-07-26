package io.github.m4gshm.payments.service;

import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import payment.v1.AccountOuterClass;
import payment.v1.AccountOuterClass.AccountListRequest;
import payment.v1.AccountOuterClass.AccountListResponse;
import payment.v1.AccountServiceGrpc;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static lombok.AccessLevel.PRIVATE;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AccountServiceImpl extends AccountServiceGrpc.AccountServiceImplBase {

    AccountStorage accountStorage;
    GrpcReactive grpc;

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
}
