package io.github.m4gshm.tpc.service;

import io.github.m4gshm.postgres.prepared.transaction.ReactivePreparedTransactionService;
import io.github.m4gshm.reactive.ReactiveGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import tpc.v1.TpcService;
import tpc.v1.TpcService.TwoPhaseCommitRequest;
import tpc.v1.TpcService.TwoPhaseCommitResponse;
import tpc.v1.TpcService.TwoPhaseListActivesRequest;
import tpc.v1.TpcService.TwoPhaseListActivesResponse;
import tpc.v1.TpcService.TwoPhaseListActivesResponse.Transaction;
import tpc.v1.TpcService.TwoPhaseRollbackRequest;
import tpc.v1.TpcService.TwoPhaseRollbackResponse;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReactiveTwoPhaseCommitServiceGrpcImpl extends
        tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceImplBase {
    ReactiveGrpc grpc;
    ReactivePreparedTransactionService transactionService;

    @Override
    public void commit(TwoPhaseCommitRequest request, StreamObserver<TpcService.TwoPhaseCommitResponse> response) {
        var requestId = request.getId();
        grpc.subscribe(
                "commit",
                response,
                () -> transactionService.commit(requestId)
                        .thenReturn(TwoPhaseCommitResponse.newBuilder()
                                .setId(requestId)
                                .build())
        );
    }

    @Override
    public void listActives(TwoPhaseListActivesRequest request, StreamObserver<TwoPhaseListActivesResponse> response) {
        grpc.subscribe(
                "listActives",
                response,
                () -> transactionService.findAll().map(transactions -> {
                    return TwoPhaseListActivesResponse.newBuilder()
                            .addAllTransactions(transactions.stream()
                                    .map(t -> Transaction.newBuilder()
                                            .setId(t.gid())
                                            .build())
                                    .toList())
                            .build();
                })
        );
    }

    @Override
    public void rollback(TwoPhaseRollbackRequest request, StreamObserver<TwoPhaseRollbackResponse> response) {
        var requestId = request.getId();
        grpc.subscribe(
                "rollback",
                response,
                () -> transactionService.rollback(requestId)
                        .thenReturn(TwoPhaseRollbackResponse.newBuilder()
                                .setId(requestId)
                                .build())
        );
    }
}
