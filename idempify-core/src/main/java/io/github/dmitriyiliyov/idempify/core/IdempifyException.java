package io.github.dmitriyiliyov.idempify.core;

/**
 * The root of every exception the library throws itself, so that a caller catches all of them with one type.
 * <p>
 * The groups under it mean different things for the call: a refusal ({@code ConflictException},
 * {@code FingerprintException}, {@link IdempotencyKeyException}) already says what to answer, while a failure of the
 * store or of a (de)serializer ({@link OperationStatusMismatchException}, {@link SerializationProcessingException},
 * {@link OperationDisappearedException}) is named by a processor as one before or after the business method
 * ({@link IdempotentProcessingException}). The root says only that the library threw it.
 */
public class IdempifyException extends RuntimeException {

    public IdempifyException(String message) {
        super(message);
    }

    public IdempifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
