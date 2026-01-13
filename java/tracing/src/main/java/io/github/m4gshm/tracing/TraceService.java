package io.github.m4gshm.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.util.context.Context;

import static io.micrometer.observation.Observation.createNotStarted;

public record TraceService(ObservationRegistry observationRegistry) {
    public Observation addEvent(Observation span, String name) {
        return span.event(Observation.Event.of(name));
    }

    public void error(Observation observation, Throwable throwable) {
        observation.error(throwable);
    }

    public Context putToReactContext(Context context, Observation trace) {
        return context.put(ObservationThreadLocalAccessor.KEY, trace);
    }

    public Observation.Scope startLocal(Observation observation) {
        return observation.openScope();
    }

    public Observation.Scope startLocalEvent(Observation span, String name) {
        return startLocal(addEvent(span, name));
    }

    public Observation startNewObservation(String spanName) {
        return newObservation(spanName).start();
    }

    public Observation newObservation(String spanName) {
        return createNotStarted(spanName, observationRegistry);
    }

    public void stop(Observation observation) {
        observation.stop();
    }

}
