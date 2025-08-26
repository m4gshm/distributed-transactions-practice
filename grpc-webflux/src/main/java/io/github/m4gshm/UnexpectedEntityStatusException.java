package io.github.m4gshm;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import lombok.Getter;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

@Getter
public class UnexpectedEntityStatusException extends InvalidStateException {
    public static final Key<String> STATUS = Key.of("status", ASCII_STRING_MARSHALLER);
    private final String entityStatus;

    public UnexpectedEntityStatusException(String entityStatus, String message) {
        super(message);
        this.entityStatus = entityStatus;
    }

    @Override
    protected Metadata newMetadata() {
        var metadata = super.newMetadata();
        metadata.put(STATUS, entityStatus);
        return metadata;
    }
}
