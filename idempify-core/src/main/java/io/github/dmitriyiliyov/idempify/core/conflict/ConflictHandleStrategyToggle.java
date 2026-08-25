package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.RawOperationMetadata;
import io.github.dmitriyiliyov.idempify.core.Toggle;

/**
 * How a call site names its {@link ConflictHandleStrategy}, with one value more than the strategy itself has:
 * the one meaning "not specified here".
 * <p>
 * The extra value is why this is a separate type rather than a constant added to
 * {@link ConflictHandleStrategy}. An annotation attribute always carries a value, so without it the
 * annotation's own default would be indistinguishable from an explicit choice and would beat every broader
 * layer. Keeping it out of the strategy also keeps that enum total: by the time a handler is chosen there is
 * nothing left to decide, and no consumer needs a branch for a value that must never arrive.
 *
 * @see Toggle
 * @see RawOperationMetadata
 */
public enum ConflictHandleStrategyToggle {

    /**
     * Not specified at this call site - the named config decides, and failing that the global properties.
     */
    UNSELECTED,

    /**
     * Explicitly {@link ConflictHandleStrategy#WAIT}, overriding whatever a broader layer says.
     */
    WAIT,

    /**
     * Explicitly {@link ConflictHandleStrategy#REJECT}, overriding whatever a broader layer says.
     */
    REJECT;

    /**
     * Maps to the resolved strategy, returning {@code null} for {@link #UNSELECTED} - "this layer did not
     * say".
     */
    public static ConflictHandleStrategy toConflictHandleStrategy(ConflictHandleStrategyToggle toggle) {
        return switch (toggle) {
            case WAIT -> ConflictHandleStrategy.WAIT;
            case REJECT -> ConflictHandleStrategy.REJECT;
            case UNSELECTED -> null;
        };
    }
}
