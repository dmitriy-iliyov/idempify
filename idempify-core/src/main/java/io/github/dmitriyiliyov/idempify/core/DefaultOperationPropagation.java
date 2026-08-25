package io.github.dmitriyiliyov.idempify.core;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * @deprecated Together with {@link OperationPropagation}, which it implements.
 */
@Deprecated(since = "0.0.1", forRemoval = true)
public record DefaultOperationPropagation<T>(
        boolean shouldPropagate,
        Optional<T> result,
        Callback<T> callback
) implements OperationPropagation<T> {

    public static <T> OperationPropagation<T> ofCallback(Callback<T> callback) {
        return new DefaultOperationPropagation<>(
                true,
                Optional.empty(),
                callback
        );
    }

    public static <T> OperationPropagation<T> ofResult(T result) {
        return new DefaultOperationPropagation<>(
                false,
                Optional.of(result),
                null
        );
    }

    public static <T> OperationPropagation<T> ofPropagatable() {
        return new DefaultOperationPropagation<>(
                true,
                Optional.empty(),
                null
        );
    }

    public static <T> OperationPropagation<T> ofNonPropagatable() {
        return new DefaultOperationPropagation<>(
                false,
                Optional.empty(),
                null
        );
    }

    @Override
    public boolean shouldPropagate() {
        return shouldPropagate;
    }

    @Override
    public T getResult() {
        return result.orElse(null);
    }

    @Override
    public Callback<T> getCallback() {
        return callback;
    }

    public record DefaultCallback<T>(
            Supplier<T> supplier,
            boolean isTransactional
    ) implements Callback<T>, TransactionAffinity {

        @Override
        public T call() {
            return supplier.get();
        }

        @Override
        public boolean requiresTransaction() {
            return isTransactional;
        }
    }
}
