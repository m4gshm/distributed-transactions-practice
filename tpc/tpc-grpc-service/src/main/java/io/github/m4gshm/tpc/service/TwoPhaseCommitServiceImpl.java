package io.github.m4gshm.tpc.service;

import io.github.m4gshm.jooq.utils.TwoPhaseTransaction;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jooq.DSLContext;
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
    DSLContext dsl;
    GrpcReactive grpc;

    @Override
    public void commit(TwoPhaseCommitRequest request, StreamObserver<TwoPhaseCommitResponse> response) {
        grpc.subscribe(response,
                       TwoPhaseTransaction.commit(dsl, request.getId())
                                          .thenReturn(TwoPhaseCommitResponse.newBuilder()
                                                                            .setId(request.getId())
                                                                            .build()));
    }

    @Override
    public void listActives(TwoPhaseListActivesRequest request, StreamObserver<TwoPhaseListActivesResponse> response) {
        grpc.subscribe(response, TwoPhaseTransaction.listPrepared(dsl).collectList().map(transactions -> {
            return TwoPhaseListActivesResponse.newBuilder()
                                              .addAllTransactions(transactions.stream()
                                                                              .map(t -> Transaction.newBuilder()
                                                                                                   .setId(t.gid())
                                                                                                   .build())
                                                                              .toList())
                                              .build();
        }));
    }

    @Override
    public void rollback(TwoPhaseRollbackRequest request, StreamObserver<TwoPhaseRollbackResponse> response) {
        grpc.subscribe(response,
                       TwoPhaseTransaction.rollback(dsl, request.getId())
                                          .thenReturn(TwoPhaseRollbackResponse.newBuilder()
                                                                              .setId(request.getId())
                                                                              .build()));
    }
}
