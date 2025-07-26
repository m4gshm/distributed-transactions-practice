package io.github.m4gshm.reactive;

import io.grpc.Status;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;

import static io.grpc.Status.INTERNAL;
import static io.grpc.Status.NOT_FOUND;
import static io.grpc.Status.PERMISSION_DENIED;
import static io.grpc.Status.UNAUTHENTICATED;

public class StatusExtractorImpl implements StatusExtractor {

    @Override
    public Status getStatus(Throwable throwable) {
        if (throwable instanceof ErrorResponse errorResponse) {
            var statusCode = errorResponse.getStatusCode();
            if (statusCode.is4xxClientError()) {
                var httpStatus = HttpStatus.resolve(statusCode.value());
                return toGrpcStatus(httpStatus);
            }
        } else {
            var responseStatus = throwable.getClass().getAnnotation(ResponseStatus.class);
            if (responseStatus != null) {
                return toGrpcStatus(responseStatus.value());
            }
        }
        return INTERNAL;
    }

    public static Status toGrpcStatus(HttpStatus httpStatus) {
        return switch (httpStatus) {
            case UNAUTHORIZED -> UNAUTHENTICATED;
            case FORBIDDEN -> PERMISSION_DENIED;
            case NOT_FOUND -> NOT_FOUND;
            case REQUEST_TIMEOUT -> Status.DEADLINE_EXCEEDED;
            default -> INTERNAL;
        };
    }

}
