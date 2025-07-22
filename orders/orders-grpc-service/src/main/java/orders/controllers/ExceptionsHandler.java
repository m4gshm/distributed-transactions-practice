package orders.controllers;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

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
