package io.github.m4gshm.reactive;

import io.grpc.Metadata;
import lombok.extern.slf4j.Slf4j;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

@Slf4j
public class MetadataFactoryImpl implements MetadataFactory {
    public static void putNotNull(Metadata trailers, String name, String value) {
        if (value != null) {
            trailers.put(Metadata.Key.of(name, ASCII_STRING_MARSHALLER), value);
        }
    }

    @Override
    public Metadata newMetadata(Throwable throwable) {
        var trailers = new Metadata();
        try {
            putNotNull(trailers, "message", throwable.getMessage());
            putNotNull(trailers, "type", throwable.getClass().getName());
        } catch (Exception e) {
            log.error("Error on subscribe errorHandling", e);
        }
        return trailers;
    }
}
