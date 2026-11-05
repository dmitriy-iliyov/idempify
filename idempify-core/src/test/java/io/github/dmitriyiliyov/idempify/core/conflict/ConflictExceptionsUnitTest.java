package io.github.dmitriyiliyov.idempify.core.conflict;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictExceptionsUnitTest {

    @ParameterizedTest
    @ValueSource(classes = {
            OperationInProgressException.class,
            WaitAbortedException.class,
            WaitTimeoutException.class,
            WaitAttemptsExhaustedException.class,
            WaitInterruptedException.class,
            ConflictHandlerResultException.class
    })
    @DisplayName("UT every exception of the conflict path should be a ConflictException so a processor lets it through as one group")
    void exceptionOfConflictPath_shouldBeConflictExceptionSoProcessorLetsItThroughAsOneGroup(Class<?> type) {
        assertThat(ConflictException.class).isAssignableFrom(type);
    }
}
