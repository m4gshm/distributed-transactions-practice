package io.github.m4gshm.jfr.controller;

import io.github.m4gshm.jfr.service.JfrRecorder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jfr")
public class JfrController {

    private static final ResponseEntity<byte[]> NOT_FOUND = ResponseEntity.notFound().build();
    private final JfrRecorder jfrRecorder;

    public static HttpHeaders newHttpHeadersForAttachmentFile(String filename) {
        var headers = new HttpHeaders();
        headers.setContentType(APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(
                ContentDisposition.builder("attachment")
                        .filename(filename)
                        .build());
        return headers;
    }

    @PostMapping
    @SneakyThrows
    public void start(@RequestParam(name = "config", required = false) String config) {
        jfrRecorder.start(config);
    }

    @PutMapping
    @SneakyThrows
    public ResponseEntity<byte[]> stop() {
        return jfrRecorder.stop().map(stream -> {
            try (var s = stream) {
                return ResponseEntity.ok()
                        .headers(newHttpHeadersForAttachmentFile("record.jfr"))
                        .body(s.readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).orElse(NOT_FOUND);
    }

}
