package orders.controllers;

import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

//@RestControllerAdvice
public class ExceptionsHandler implements WebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        return null;
    }

//    @ExceptionHandler
//    public ResponseEntity<Object> handleUnexpectedException(Exception ex, WebRequest request) throws Exception {
//        var status = INTERNAL_SERVER_ERROR;
//        var body = createProblemDetail(ex, status, ex.getLocalizedMessage(), ex.getClass().getName(),
//                new Object[0], request);
//        return ResponseEntity.status(status).body(body);
//    }

}
