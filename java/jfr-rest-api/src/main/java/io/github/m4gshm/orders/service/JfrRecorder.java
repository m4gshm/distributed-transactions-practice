package io.github.m4gshm.orders.service;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static jdk.jfr.Configuration.getConfiguration;

@Service
public class JfrRecorder {
    final AtomicReference<Recording> record = new AtomicReference<>();
    final Configuration configuration;

    {
        try {
            configuration = getConfiguration("default");
        } catch (IOException | ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    @PostMapping
    @SneakyThrows
    public void start() {
        var newRec = new Recording(configuration);
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
