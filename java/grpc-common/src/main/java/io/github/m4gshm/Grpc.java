package io.github.m4gshm;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public record Grpc(GrpcExceptionConverter grpcExceptionConverter) {
    public <R> void subscribe(String op,
                              StreamObserver<R> responseObserver,
                              Supplier<R> routine) {
        try {
            var response = routine.get();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(grpcExceptionConverter.convertToGrpcStatusException(e));
            log.error("grpc service error", e);
        }
    }
}
