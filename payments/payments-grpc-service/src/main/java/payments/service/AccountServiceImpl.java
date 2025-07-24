package payments.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import payment.v1.AccountOuterClass;
import payment.v1.AccountOuterClass.AccountListRequest;
import payment.v1.AccountOuterClass.AccountListResponse;
import payment.v1.AccountServiceGrpc;
import payments.data.AccountStorage;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static io.github.m4gshm.reactive.GrpcUtils.subscribe;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl extends AccountServiceGrpc.AccountServiceImplBase {

    private final AccountStorage accountStorage;

    @Override
    public void list(AccountListRequest request, StreamObserver<AccountListResponse> responseObserver) {
        subscribe(responseObserver, accountStorage.findAll().map(accounts -> {
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
