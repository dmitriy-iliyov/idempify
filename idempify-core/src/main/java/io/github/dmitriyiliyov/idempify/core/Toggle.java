package io.github.dmitriyiliyov.idempify.core;

/**
 * A three-valued switch for the settings a call site may leave to a broader layer.
 * <p>
 * A plain {@code boolean} could not express "not specified here": an annotation attribute always has a value,
 * so {@code false} would be indistinguishable from "left alone" and a call site could never inherit a setting
 * it did not mention. This is why {@code @Idempotent} declares its switches as {@code Toggle} rather than
 * {@code boolean}.
 *
 * @see RawOperationMetadata
 */
public enum Toggle {

    /**
     * Not specified.
     */
    UNSELECTED,

    /**
     * Explicitly on, overriding whatever a broader layer says.
     */
    ENABLE,

    /**
     * Explicitly off, overriding whatever a broader layer says.
     */
    DISABLE;

    /**
     * Collapses the three values into the two a merge works with, returning {@code null} for
     * {@link #UNSELECTED} - "this layer did not say".
     */
    public static Boolean toBoolean(Toggle toggle) {
        return switch (toggle) {
            case ENABLE -> true;
            case DISABLE -> false;
            case UNSELECTED -> null;
        };
    }
}
