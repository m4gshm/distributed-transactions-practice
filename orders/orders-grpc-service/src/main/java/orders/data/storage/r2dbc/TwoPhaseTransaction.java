package orders.data.storage.r2dbc;

import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import reactor.core.publisher.Mono;

import static reactor.core.publisher.Mono.from;

@UtilityClass
public class TwoPhaseTransaction {
    public static <T> Mono<T> prepare(Mono<T> routine, DSLContext dsl, String id) {
        return routine.flatMap(element -> prepare(dsl, id).thenReturn(element));
    }

    public static Mono<Void> prepare(DSLContext dsl, String id) {
        return from(dsl.query("PREPARE TRANSACTION '" + id + "'")).then();
    }

    public static Mono<Void> commit(DSLContext dsl, String id) {
        return from(dsl.query("COMMIT PREPARED '" + id + "'")).then();
    }

    public static Mono<Void> rollback(DSLContext dsl, String id) {
        return from(dsl.query("ROLLBACK PREPARED '" + id + "'")).then();
    }
}
