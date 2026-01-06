package io.github.m4gshm;

import grpcstarter.extensions.transcoding.TranscodingRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.MESSAGE;
import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.PATH;
import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.STATUS;

//@ControllerAdvice
@RequiredArgsConstructor
public class RestControllerExceptionHandler {

    private final ErrorAttributes errorAttributes;

    @ExceptionHandler(TranscodingRuntimeException.class)
    public ResponseEntity<?> handle(TranscodingRuntimeException exception, HttpServletRequest request) {
        WebRequest webRequest = new ServletWebRequest(request);
        var errorAttributes = this.errorAttributes.getErrorAttributes(webRequest,
                ErrorAttributeOptions.of(PATH, STATUS, MESSAGE));
        errorAttributes.put("headers", exception.getHeaders());
        return ResponseEntity.status(exception.getStatusCode()).body(errorAttributes);
    }

}
