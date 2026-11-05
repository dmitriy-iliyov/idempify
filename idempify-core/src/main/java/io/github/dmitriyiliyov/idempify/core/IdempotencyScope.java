package io.github.dmitriyiliyov.idempify.core;

/**
 * Marks the thread on which an idempotent operation is running, so that an entry can refuse a call made from
 * inside it: an operation nested in another one is covered by the outer one's key and has nothing to add.
 * <p>
 * Every entry asks before it does any work on a call, and refuses it with {@link NestedIdempotencyException}
 * while the scope is active; otherwise it activates the scope for the whole call, the business method included,
 * and deactivates it in a {@code finally}. The check is the entry's rather than the processor's because the
 * entry works on a call before it reaches the processor - the key, the fingerprint, the metadata - and a failure
 * there, thrown from inside the outer business method, would be kept as the outer operation's outcome.
 * <p>
 * A call made on another thread - an {@code @Async} method, a {@code CompletableFuture} - does not see the scope.
 */
public final class IdempotencyScope {

    private static final ThreadLocal<Boolean> LOCAL = new ThreadLocal<>();

    private IdempotencyScope() {}

    public static boolean isActive() {
        return LOCAL.get() != null;
    }

    public static void activate() {
        LOCAL.set(true);
    }

    public static void deactivate() {
        LOCAL.remove();
    }
}
