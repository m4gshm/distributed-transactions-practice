package io.github.m4gshm.tpc.service;

import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import tpc.v1.Tpc.TwoPhaseCommitRequest;
import tpc.v1.Tpc.TwoPhaseCommitResponse;
import tpc.v1.Tpc.TwoPhaseListActivesRequest;
import tpc.v1.Tpc.TwoPhaseListActivesResponse;
import tpc.v1.Tpc.TwoPhaseListActivesResponse.Transaction;
import tpc.v1.Tpc.TwoPhaseRollbackRequest;
import tpc.v1.Tpc.TwoPhaseRollbackResponse;
import tpc.v1.TwoPhaseCommitServiceGrpc;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class TwoPhaseCommitServiceImpl extends TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceImplBase {
    GrpcReactive grpc;
    PreparedTransactionService transactionService;

    @Override
    public void commit(TwoPhaseCommitRequest request, StreamObserver<TwoPhaseCommitResponse> response) {
        var requestId = request.getId();
        grpc.subscribe(
                response,
                transactionService.commit(requestId)
                        .thenReturn(TwoPhaseCommitResponse.newBuilder()
                                .setId(requestId)
                                .build())
        );
    }

    @Override
    public void listActives(TwoPhaseListActivesRequest request, StreamObserver<TwoPhaseListActivesResponse> response) {
        grpc.subscribe(
                response,
                transactionService.findAll().map(transactions -> {
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
                response,
                transactionService.rollback(requestId)
                        .thenReturn(TwoPhaseRollbackResponse.newBuilder()
                                .setId(requestId)
                                .build())
        );
    }
}
