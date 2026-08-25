package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelegatingIdempotentProcessorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT constructor when processors is null should throw NullPointerException")
    void constructor_whenProcessorsIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DelegatingIdempotentProcessor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("processors cannot be null");
    }

    @Test
    @DisplayName("UT constructor when processors is empty should throw IllegalStateException naming the missing type")
    void constructor_whenProcessorsIsEmpty_shouldThrowIllegalStateExceptionNamingMissingType() {
        assertThatThrownBy(() -> new DelegatingIdempotentProcessor(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TypeAwareIdempotentProcessor")
                .hasMessageContaining("none was found");
    }

    @Test
    @DisplayName("UT constructor when two processors serve the same type should throw IllegalStateException naming both")
    void constructor_whenTwoProcessorsServeSameType_shouldThrowIllegalStateExceptionNamingBoth() {
        // given
        RecordingProcessor first = new RecordingProcessor(ProcessorType.LOCK_BASED, "from-first");
        RecordingProcessor second = new RecordingProcessor(ProcessorType.LOCK_BASED, "from-second");

        // when / then
        assertThatThrownBy(() -> new DelegatingIdempotentProcessor(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProcessorType.LOCK_BASED.name())
                .hasMessageContaining(RecordingProcessor.class.getName());
    }

    @Test
    @DisplayName("UT process() should route the call to the processor the metadata names")
    void process_shouldRouteCallToProcessorMetadataNames() {
        // given
        RecordingProcessor transactional = new RecordingProcessor(ProcessorType.TRANSACTIONAL, "from-transactional");
        RecordingProcessor lockBased = new RecordingProcessor(ProcessorType.LOCK_BASED, "from-lock-based");
        DelegatingIdempotentProcessor tested = new DelegatingIdempotentProcessor(List.of(transactional, lockBased));

        // when
        String result = tested.process(context(), metadata(ProcessorType.LOCK_BASED));

        // then
        assertThat(result).isEqualTo("from-lock-based");
        assertThat(lockBased.calls).isEqualTo(1);
        assertThat(transactional.calls).isZero();
    }

    @Test
    @DisplayName("UT process() when no processor serves the named type should throw IllegalStateException naming the key")
    void process_whenNoProcessorServesNamedType_shouldThrowIllegalStateExceptionNamingKey() {
        // given
        DelegatingIdempotentProcessor tested = new DelegatingIdempotentProcessor(
                List.of(new RecordingProcessor(ProcessorType.TRANSACTIONAL, "from-transactional")));

        // when / then
        assertThatThrownBy(() -> tested.process(context(), metadata(ProcessorType.LOCK_BASED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no processor found")
                .hasMessageContaining(KEY.toString());
    }

    private OperationContext<String> context() {
        return new DefaultOperationContext<>(String.class, () -> "fresh", KEY, "fingerprint");
    }

    private OperationMetadata metadata(ProcessorType processorType) {
        return TestOperationMetadata.builder().processorType(processorType).build();
    }

    private static final class RecordingProcessor implements TypeAwareIdempotentProcessor {

        private final ProcessorType type;
        private final Object result;
        private int calls;

        private RecordingProcessor(ProcessorType type, Object result) {
            this.type = type;
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T process(OperationContext<T> context, OperationMetadata metadata) {
            calls++;
            return (T) result;
        }

        @Override
        public ProcessorType getType() {
            return type;
        }
    }
}
