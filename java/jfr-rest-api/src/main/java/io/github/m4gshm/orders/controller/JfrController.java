package io.github.m4gshm.orders.controller;

import io.github.m4gshm.orders.service.JfrRecorder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.InputStream;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@Controller
@RequiredArgsConstructor
@RequestMapping("api/v1/jfr")
public class JfrController {

    private static final ResponseEntity<InputStream> NOT_FOUND = ResponseEntity.notFound().build();
    private final JfrRecorder jfrRecorder;

    @PostMapping
    @SneakyThrows
    public void start() {
        jfrRecorder.start();
    }

    @PutMapping
    @SneakyThrows
    public ResponseEntity<InputStream> stop() {
        return jfrRecorder.stop().map(stream -> {
            var headers = new HttpHeaders();
            headers.setContentType(APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(
                    ContentDisposition.builder("attachment")
                            .filename("record.jfr")
                            .build());
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(stream);
        }).orElse(NOT_FOUND);
    }

}
