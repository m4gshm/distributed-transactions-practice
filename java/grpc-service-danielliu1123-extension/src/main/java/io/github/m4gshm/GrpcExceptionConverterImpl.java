package io.github.m4gshm;

import grpcstarter.server.feature.exceptionhandling.GrpcExceptionResolver;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GrpcExceptionConverterImpl implements GrpcExceptionConverter {
    MetadataFactory metadataFactory;
    StatusExtractor statusExtractor;
    List<GrpcExceptionResolver> grpcExceptionResolvers;

    @Override
    public Throwable convertToGrpcStatusException(Throwable throwable) {
        if (throwable instanceof GrpcConvertible grpcConvertible) {
            return grpcConvertible.toGrpcRuntimeException();
        } else if (throwable instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException;
        } else if (throwable instanceof StatusException statusException) {
            return statusException;
        }
        final var metadata = metadataFactory.newMetadata(throwable);
        for (var resolver : grpcExceptionResolvers) {
            var sre = resolver.resolve(throwable, null, metadata);
            if (sre != null) {
                return sre;
            }
        }
        return new StatusRuntimeException(statusExtractor.getStatus(throwable), metadata);
    }
}
