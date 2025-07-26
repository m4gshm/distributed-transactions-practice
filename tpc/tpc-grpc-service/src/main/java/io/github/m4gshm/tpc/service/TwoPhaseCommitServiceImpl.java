package io.github.m4gshm.tpc.service;

import io.grpc.stub.StreamObserver;
import io.github.m4gshm.jooq.utils.TwoPhaseTransaction;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import tpc.v1.Tpc.TwoPhaseCommitRequest;
import tpc.v1.Tpc.TwoPhaseCommitResponse;
import tpc.v1.Tpc.TwoPhaseRollbackRequest;
import tpc.v1.Tpc.TwoPhaseRollbackResponse;
import tpc.v1.TwoPhaseCommitServiceGrpc;

import static io.github.m4gshm.reactive.GrpcUtils.subscribe;

@RequiredArgsConstructor
public class TwoPhaseCommitServiceImpl extends TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceImplBase {
    private final DSLContext dsl;

    @Override
    public void commit(TwoPhaseCommitRequest request, StreamObserver<TwoPhaseCommitResponse> response) {
        subscribe(response, TwoPhaseTransaction.commit(dsl, request.getId()).thenReturn(
                TwoPhaseCommitResponse.newBuilder().setId(request.getId()).build()
        ));
    }

    @Override
    public void rollback(TwoPhaseRollbackRequest request, StreamObserver<TwoPhaseRollbackResponse> response) {
        subscribe(response, TwoPhaseTransaction.rollback(dsl, request.getId()).thenReturn(
                TwoPhaseRollbackResponse.newBuilder().setId(request.getId()).build()
        ));
    }
}
