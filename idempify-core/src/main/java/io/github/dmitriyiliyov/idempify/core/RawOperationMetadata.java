package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;

import java.time.Duration;

/**
 * What one call site asked for, exactly as its {@link Idempotent} spelled it - the most specific of the
 * configuration layers and the only one written next to the code it governs.
 * <p>
 * Every getter may answer "not specified here", each in the way its type allows: {@code null} for a value,
 * {@link Toggle#UNSELECTED} for a switch, {@link ProcessorTypeToggle#UNSELECTED} and
 * {@link ConflictHandleStrategyToggle#UNSELECTED} for the two choices. {@link OperationMetadataManager} layers a
 * named config and the global properties underneath and turns the result into an {@link OperationMetadata},
 * where nothing is left undecided.
 */
public interface RawOperationMetadata {

    boolean useHeaderName();

    String getHeaderName();

    Duration getTtl();

    ProcessorTypeToggle getProcessorType();

    ConflictHandleStrategyToggle getConflictHandleStrategy();

    Toggle getFingerprintToggle();

    Toggle getCache4xxToggle();

    Toggle getCache5xxToggle();
}
