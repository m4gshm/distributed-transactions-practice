package io.github.m4gshm.orders.data.storage.r2dbc;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static reactor.core.publisher.Flux.usingWhen;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.usingWhen;

@Slf4j
@Component
@RequiredArgsConstructor
public class R2DBCConnection {
    private final ConnectionFactory connectionFactory;

    @NotNull
    private Mono<? extends Connection> connection(boolean autoCommit) {
        var conn = from(connectionFactory.create());
        return autoCommit ? conn.flatMap(c -> from(c.setAutoCommit(true)).thenReturn(c)) : conn;
    }

    @NotNull
    public <T> Flux<T> flux(boolean autoCommit, Function<Connection, Flux<T>> routine) {
        return usingWhen(connection(autoCommit), routine, Connection::close)
                                                                            .doOnError(e -> {
                                                                                log.error("connection flux error", e);
                                                                            });
    }

    @NotNull
    public <T> Mono<T> mono(Function<Connection, Mono<T>> routine) {
        return usingWhen(connection(false), routine, Connection::close)
                                                                       .doOnError(e -> {
                                                                           log.error("connection mono error", e);
                                                                       });
    }

    @NotNull
    public <T> Mono<T> transactMono(Function<Connection, Mono<T>> routine) {
        return mono(c -> from(c.beginTransaction())
                                                   .then(routine.apply(c))
                                                   .flatMap(t -> from(c.commitTransaction()).thenReturn(t)))
                                                                                                            .doOnError(e -> {
                                                                                                                log.error("connection transaction mono error",
                                                                                                                          e);
                                                                                                            });
    }
}
