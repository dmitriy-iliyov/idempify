package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempifyDefaultsUnitTest {

    @Test
    @DisplayName("UT recordForOf() when the processor is external should keep every failure")
    void recordForOf_whenProcessorIsExternal_shouldKeepEveryFailure() {
        // when / then
        assertThat(IdempifyDefaults.recordForOf(ProcessorType.EXTERNAL_NON_BLOCKING))
                .containsExactly(Throwable.class);
    }

    @Test
    @DisplayName("UT recordForOf() when the processor is transactional should keep no failure")
    void recordForOf_whenProcessorIsTransactional_shouldKeepNoFailure() {
        // when / then
        assertThat(IdempifyDefaults.recordForOf(ProcessorType.TRANSACTIONAL_NON_BLOCKING)).isEmpty();
        assertThat(IdempifyDefaults.recordForOf(ProcessorType.TRANSACTIONAL_BLOCKING)).isEmpty();
    }
}
