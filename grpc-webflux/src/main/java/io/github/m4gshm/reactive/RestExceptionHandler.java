package io.github.m4gshm.reactive;

import grpcstarter.extensions.transcoding.TranscodingRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.*;

@ControllerAdvice
@RequiredArgsConstructor
public class RestExceptionHandler {

    private final ErrorAttributes errorAttributes;
    private final List<HttpMessageReader<?>> messageReaders;

    @ExceptionHandler(TranscodingRuntimeException.class)
    public ResponseEntity<?> handle(TranscodingRuntimeException exception, ServerWebExchange exchange) {
        errorAttributes.storeErrorInformation(exception, exchange);
        var serverRequest = ServerRequest.create(exchange, messageReaders);
        var errorAttributes = this.errorAttributes.getErrorAttributes(serverRequest,
                                                                      ErrorAttributeOptions.of(PATH, STATUS, MESSAGE));
        errorAttributes.put("headers", exception.getHeaders());
        return ResponseEntity.status(exception.getStatusCode()).body(errorAttributes);
    }

}
