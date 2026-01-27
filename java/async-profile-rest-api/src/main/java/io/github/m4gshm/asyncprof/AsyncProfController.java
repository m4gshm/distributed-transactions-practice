package io.github.m4gshm.asyncprof;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import one.profiler.AsyncProfiler;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.m4gshm.asyncprof.AsyncProfController.Event.cpu;
import static io.github.m4gshm.asyncprof.AsyncProfController.Format.flamegraph;
import static java.io.File.createTempFile;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/asyncprof")
public class AsyncProfController {

    private static final ResponseEntity<byte[]> NOT_FOUND = ResponseEntity.notFound().build();
    private final AsyncProfiler profiler = getInstance();
    private final AtomicReference<File> profilerOut = new AtomicReference<>();
    private volatile Format format;

    private static AsyncProfiler getInstance() {
        try {
            return AsyncProfiler.getInstance();
        } catch (UnsupportedOperationException e) {
            log.error("init error", e);
            return null;
        }
    }

    public static HttpHeaders newHttpHeadersForAttachmentFile(String filename) {
        var headers = new HttpHeaders();
        headers.setContentType(APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(
                ContentDisposition.builder("attachment")
                        .filename(filename)
                        .build());
        return headers;
    }

    private static String toString(DataSize dataSize) {
        var megabytes = dataSize.toMegabytes();
        if (megabytes > 0) {
            return megabytes + "mb";
        }
        var kilobytes = dataSize.toKilobytes();
        if (kilobytes > 0) {
            return kilobytes + "kb";
        }
        var bytes = dataSize.toBytes();
        return bytes + "b";
    }

    @PostMapping
    @SneakyThrows
    public void start(@RequestParam(name = "event", required = false) Event event,
                      @RequestParam(name = "format", required = false) Format format,
                      @RequestParam(name = "options", required = false) String options) {
        var usedEvent = event != null ? event : cpu;
        var usedFormat = format != null ? format : flamegraph;
        var file = createTempFile("asyncprof", usedFormat.name());
        if (profilerOut.compareAndSet(null, file)) {
            this.format = usedFormat;
            var command = "start," + usedFormat.name()
                    + ",event="
                    + usedEvent
                    +
                    (options == null || options.isBlank() ? "" : "," + options);
            log.info("start profiling command {}", command);
            var execute = profiler.execute(command);
            log.info("start result {}", execute);
        } else {
            file.delete();
            throw new IllegalStateException("already started");
        }
    }

    @PutMapping
    @SneakyThrows
    public ResponseEntity<byte[]> stop() {
        var file = profilerOut.get();
        if (file != null && profilerOut.compareAndSet(file, null)) {
            var command = "stop," + format.name() + ",file=" + file.getAbsolutePath();
            log.info("stop profiling command {}", command);
            var execute = profiler.execute(command);
            log.info("stop result '{}', result file {}", execute, file.getAbsolutePath());

            try (var s = new FileInputStream(file)) {
                var payload = s.readAllBytes();
                var dataSize = DataSize.ofBytes(payload.length);
                log.info("response payload size {}", toString(dataSize));
                return ResponseEntity.ok()
                        .headers(newHttpHeadersForAttachmentFile(file.getName() + "."
                                +
                                format.fileExtension))
                        .body(payload);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                file.delete();
            }
        }
        return NOT_FOUND;
    }

    @RequiredArgsConstructor
    public enum Event {
            cpu,
            ctimer,
            ;
    }

    @RequiredArgsConstructor
    public enum Format {
            jfr("jfr"),
            flamegraph("html"),
            tree("html"),
            ;

        public final String fileExtension;
    }

}
