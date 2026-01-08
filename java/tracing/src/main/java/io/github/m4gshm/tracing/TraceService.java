package io.github.m4gshm.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class TraceService {
    ObservationRegistry observationRegistry;

    public Observation addEvent(Observation span, String name) {
        return span.event(Observation.Event.of(name));
    }

    public Span addEvent(Span span, String name) {
        return span.addEvent(name);
    }

    public void error(Observation observation, Throwable throwable) {
        observation.error(throwable);
    }

    public @Nullable Observation getLocal() {
        return observationRegistry.getCurrentObservation();
    }

    public void setLocal(Observation.Scope scope) {
        observationRegistry.setCurrentObservationScope(scope);
    }

    public reactor.util.context.Context putToReactContext(reactor.util.context.Context context, Observation trace) {
        return context.put(ObservationThreadLocalAccessor.KEY, trace);
    }

    public Observation.Scope startLocal(Observation observation) {
        return observation.openScope();
    }

    public Scope startLocal(Span span) {
        return span.makeCurrent();
    }

    public Observation.Scope startLocalEvent(Observation span, String name) {
        return startLocal(addEvent(span, name));
    }

    public Scope startLocalEvent(Span span, String name) {
        return startLocal(addEvent(span, name));
    }

    public Observation startNewObservation(String spanName) {
        return Observation.createNotStarted(spanName, observationRegistry).start();
    }

//    public Span startNewSpan(String spanName) {
//        return otelTracer.spanBuilder(spanName).setParent(Context.current()).startSpan();
//    }

    public void stop(Observation observation) {
        observation.stop();
    }

}
