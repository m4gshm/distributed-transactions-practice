package io.github.m4gshm.reactive;

import grpcstarter.server.feature.exceptionhandling.annotation.GrpcAdvice;
import grpcstarter.server.feature.exceptionhandling.annotation.GrpcExceptionHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.exception.IntegrityConstraintViolationException;

import static io.grpc.Status.INVALID_ARGUMENT;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@GrpcAdvice
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class DefaultGrpcExceptionHandler {

    MetadataFactory metadataFactory;
    StatusExtractor statusExtractor;

    @GrpcExceptionHandler(Exception.class)
    public StatusRuntimeException handle(Exception e) {
        return newStatusRuntimeException(statusExtractor.getStatus(e), e);
    }

    @GrpcExceptionHandler(IntegrityConstraintViolationException.class)
    public StatusRuntimeException handle(IntegrityConstraintViolationException e) {
        return newStatusRuntimeException(INVALID_ARGUMENT, e);
    }

    private StatusRuntimeException newStatusRuntimeException(Status status, Throwable e) {
        return status.withDescription(e.getMessage()).asRuntimeException(metadataFactory.newMetadata(e));
    }
}
