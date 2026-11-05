package io.github.dmitriyiliyov.idempify.core;

/**
 * How a call site names its {@link ProcessorType}, with one value more than the type itself has: the one
 * meaning "not specified here".
 * <p>
 * The extra value is why this is a separate type rather than a constant added to {@link ProcessorType}. An
 * annotation attribute always carries a value, so without it the annotation's own default would be
 * indistinguishable from an explicit choice and would beat every broader layer. Keeping it out of
 * {@link ProcessorType} also keeps that type total: by the time a processor is selected there is nothing left
 * to decide, and no consumer needs a branch for a value that must never arrive.
 *
 * @see Toggle
 * @see RawOperationMetadata
 */
public enum ProcessorTypeToggle {

    /**
     * Not specified at this call site - the named config decides, and failing that the global properties.
     */
    UNSELECTED,

    /**
     * Explicitly {@link ProcessorType#TRANSACTIONAL_BLOCKING}, overriding whatever a broader layer says.
     */
    TRANSACTIONAL_BLOCKING,

    /**
     * Explicitly {@link ProcessorType#TRANSACTIONAL_NON_BLOCKING}, overriding whatever a broader layer says.
     */
    TRANSACTIONAL_NON_BLOCKING,

    /**
     * Explicitly {@link ProcessorType#EXTERNAL_NON_BLOCKING}, overriding whatever a broader layer says.
     */
    EXTERNAL_NON_BLOCKING;

    /**
     * Maps to the resolved type, returning {@code null} for {@link #UNSELECTED} - "this layer did not say".
     */
    public static ProcessorType toProcessorType(ProcessorTypeToggle toggle) {
        return switch (toggle) {
            case TRANSACTIONAL_BLOCKING -> ProcessorType.TRANSACTIONAL_BLOCKING;
            case TRANSACTIONAL_NON_BLOCKING -> ProcessorType.TRANSACTIONAL_NON_BLOCKING;
            case EXTERNAL_NON_BLOCKING -> ProcessorType.EXTERNAL_NON_BLOCKING;
            case UNSELECTED -> null;
        };
    }
}
