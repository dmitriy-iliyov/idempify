package io.github.dmitriyiliyov.idempify.core;

import java.util.Objects;

/**
 * Counts a call that failed for a reason outside its operation as {@link IdempotencyEventListener#onFailure},
 * of the type {@link IdempotentProcessorUtils#libraryFailureTypeOf} names from what was thrown, and throws that
 * very failure on. A failure of the operation itself, a refusal and an {@link Error} are not counted here.
 * <p>
 * The processors below only throw: each names where a call failed by the type of the exception, and this is the
 * one place the type becomes an event, so what the caller is thrown and what is counted cannot say different
 * things and nothing is counted twice.
 */
public class FailureObservingIdempotentProcessorDecorator extends AbstractIdempotentProcessorDecorator {

    private final IdempotencyEventListener eventListener;

    public FailureObservingIdempotentProcessorDecorator(IdempotentProcessor delegate,
                                                        IdempotencyEventListener eventListener) {
        super(delegate);
        this.eventListener = new SwallowIdempotencyEventListenerDecorator(
                Objects.requireNonNull(eventListener, "eventListener cannot be null")
        );
    }

    @Override
    public Object process(OperationContext context, OperationMetadata metadata) throws Throwable {
        try {
            return super.process(context, metadata);
        } catch (Throwable t) {
            FailureType failureType = IdempotentProcessorUtils.libraryFailureTypeOf(t);
            if (failureType != null) {
                eventListener.onFailure(failureType);
            }
            throw t;
        }
    }
}
