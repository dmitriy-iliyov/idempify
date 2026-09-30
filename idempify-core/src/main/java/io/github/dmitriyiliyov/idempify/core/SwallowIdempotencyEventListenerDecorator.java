package io.github.dmitriyiliyov.idempify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class SwallowIdempotencyEventListenerDecorator implements IdempotencyEventListener {

    private static final Logger log = LoggerFactory.getLogger(SwallowIdempotencyEventListenerDecorator.class);
    private final IdempotencyEventListener delegate;

    public SwallowIdempotencyEventListenerDecorator(IdempotencyEventListener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public void onDuplicate() {
        wrap(delegate::onDuplicate);
    }

    @Override
    public void onConflict() {
        wrap(delegate::onConflict);
    }

    @Override
    public void onFingerprintMismatch() {
        wrap(delegate::onFingerprintMismatch);
    }

    @Override
    public void onException() {
        wrap(delegate::onException);
    }

    @Override
    public void onSuccess() {
        wrap(delegate::onSuccess);
    }

    @Override
    public void onExceptionallySuccess() {
        wrap(delegate::onExceptionallySuccess);
    }

    private static void wrap(Runnable call) {
        try {
            call.run();
        } catch (Throwable t) {
            log.error("IdempotencyEventListener throws", t);
        }
    }
}
