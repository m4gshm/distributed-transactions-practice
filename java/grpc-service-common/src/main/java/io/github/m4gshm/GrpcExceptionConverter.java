package io.github.m4gshm;

public interface GrpcExceptionConverter {
    Throwable convertToGrpcStatusException(Throwable throwable);
}
