package io.github.m4gshm.jfr.service;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static jdk.jfr.Configuration.getConfiguration;

@Slf4j
public class JfrRecorder {
    final AtomicReference<Recording> record = new AtomicReference<>();

    private static Configuration newConfiguration(String name) {
        try {
            final var configuration = getConfiguration(name);
            log.info("jfr configuration name {}, settings {}", configuration.getName(), configuration.getSettings());
            return configuration;
        } catch (IOException | ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    @PostMapping
    @SneakyThrows
    public void start(@RequestParam(required = false) String config) {
        var newRec = new Recording(newConfiguration(config == null || config.isBlank() ? "default" : config));
        if (!record.compareAndSet(null, newRec)) {
            throw new IllegalStateException("already started");
        }
        newRec.start();
    }

    @PutMapping
    @SneakyThrows
    public Optional<InputStream> stop() {
        var recording = record.get();
        if (recording == null || !record.compareAndSet(recording, null)) {
            return Optional.empty();
        }
        recording.stop();
        var output = Files.createTempFile("record", ".jfr");
        recording.dump(output);
        var outputFile = output.toFile();

        return Optional.of(new FileInputStream(outputFile));
    }
}
