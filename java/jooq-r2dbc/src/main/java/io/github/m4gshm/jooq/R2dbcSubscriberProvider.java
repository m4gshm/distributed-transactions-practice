package io.github.m4gshm.jooq;

import org.jooq.SubscriberProvider;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import java.util.function.Consumer;

public class R2dbcSubscriberProvider implements SubscriberProvider<Object> {

    @Override
    public Object context() {
        return Context.empty();
    }

    @Override
    public Object context(Subscriber<?> subscriber) {
        if (subscriber instanceof CoreSubscriber<?> coreSubscriber) {
            return coreSubscriber.currentContext();
        }
        // log
        return context();
    }

    @Override
    public <T> Subscriber<T> subscriber(Consumer<? super Subscription> onSubscribe,
                                        Consumer<? super T> onNext,
                                        Consumer<? super Throwable> onError,
                                        Runnable onComplete,
                                        Object context) {
        return new CoreSubscriber<T>() {
            @Override
            public Context currentContext() {
                if (context instanceof Context rc) {
                    return rc;
                } else {
                    // log
                    return Context.empty();
                }
            }

            @Override
            public void onComplete() {
                onComplete.run();
            }

            @Override
            public void onError(Throwable t) {
                onError.accept(t);
            }

            @Override
            public void onNext(T t) {
                onNext.accept(t);
            }

            @Override
            public void onSubscribe(Subscription s) {
                onSubscribe.accept(s);
            }
        };
    }
}
