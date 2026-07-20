package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OperationMetadataUnitTest {

    @Test
    @DisplayName("UT builder() when valid arguments provided should create metadata")
    void builder_whenValidArgumentsProvided_shouldCreateMetadata() {
        // given
        UUID idempotencyKey = UUID.randomUUID();

        // when
        OperationMetadata result = OperationMetadata.builder()
                .idempotencyKey(idempotencyKey)
                .ttl(100L)
                .timeUnit(TimeUnit.SECONDS)
                .conflictHandleStrategy(ConflictHandleStrategy.REJECT)
                .build();

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(result.getTtl()).isEqualTo(100L);
        assertThat(result.getTimeUnit()).isEqualTo(TimeUnit.SECONDS);
        assertThat(result.getConflictHandleStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
        assertFalse(result.useFingerprint());
    }

    @Test
    @DisplayName("UT idempotencyKey() when value is null should throw NullPointerException")
    void idempotencyKey_whenValueIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> OperationMetadata.builder()
                .idempotencyKey(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idempotencyKey cannot be null");
    }

    @Test
    @DisplayName("UT ttl() when value is null should throw NullPointerException")
    void ttl_whenValueIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> OperationMetadata.builder()
                .ttl(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ttl cannot be null");
    }

    @Test
    @DisplayName("UT ttl() when value is negative should throw IllegalArgumentException")
    void ttl_whenValueIsNegative_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> OperationMetadata.builder()
                .ttl(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl cannot be negative");
    }

    @Test
    @DisplayName("UT timeUnit() when value is null should throw NullPointerException")
    void timeUnit_whenValueIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> OperationMetadata.builder()
                .timeUnit(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timeUnit cannot be null");
    }

    @Test
    @DisplayName("UT conflictHandleStrategy() when value is null should throw NullPointerException")
    void conflictHandleStrategy_whenValueIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> OperationMetadata.builder()
                .conflictHandleStrategy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conflictHandleStrategy cannot be null");
    }

    @Test
    @DisplayName("UT build() when conflict strategy is CUSTOM and handler class is null should throw NullPointerException")
    void build_whenConflictStrategyCustomAndHandlerClassIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> OperationMetadata.builder()
                .conflictHandleStrategy(ConflictHandleStrategy.CUSTOM)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conflictHandlerClass cannot be null when conflictHandleStrategy is CUSTOM");
    }

    @Test
    @DisplayName("UT build() when conflict strategy is CUSTOM and handler class exists should create metadata")
    void build_whenConflictStrategyCustomAndHandlerClassExists_shouldCreateMetadata() {
        // given
        Class<? extends ConflictHandler> handlerClass = TestConflictHandler.class;

        // when
        OperationMetadata result = OperationMetadata.builder()
                .conflictHandleStrategy(ConflictHandleStrategy.CUSTOM)
                .conflictHandlerClass(handlerClass)
                .build();

        // then
        assertThat(result.getConflictHandlerClass()).isEqualTo(handlerClass);
        assertThat(result.getConflictHandleStrategy()).isEqualTo(ConflictHandleStrategy.CUSTOM);
    }

    @Test
    @DisplayName("UT build() when fingerprint is enabled and fingerprint is null should throw NullPointerException")
    void build_whenFingerprintEnabledAndFingerprintIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> OperationMetadata.builder()
                .useFingerprint(true)
                .fingerprintPolicyClass(TestFingerprintPolicy.class)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprint cannot be null when useFingerprint is true");
    }

    @Test
    @DisplayName("UT build() when fingerprint is enabled and policy class is null should throw NullPointerException")
    void build_whenFingerprintEnabledAndPolicyClassIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> OperationMetadata.builder()
                .useFingerprint(true)
                .fingerprint("fingerprint")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintPolicyClass cannot be null when useFingerprint is true");
    }

    @Test
    @DisplayName("UT build() when fingerprint is enabled and all values exist should create metadata")
    void build_whenFingerprintEnabledAndAllValuesExist_shouldCreateMetadata() {
        // given
        String fingerprint = "fingerprint";

        // when
        OperationMetadata result = OperationMetadata.builder()
                .useFingerprint(true)
                .fingerprint(fingerprint)
                .fingerprintPolicyClass(TestFingerprintPolicy.class)
                .build();

        // then
        assertTrue(result.useFingerprint());
        assertThat(result.getFingerprint()).isEqualTo(fingerprint);
        assertThat(result.getFingerprintPolicyClass()).isEqualTo(TestFingerprintPolicy.class);
    }

    @Test
    @DisplayName("UT conflictHandlerClass() when value is null and strategy is not CUSTOM should create metadata")
    void conflictHandlerClass_whenValueIsNullAndStrategyIsNotCustom_shouldCreateMetadata() {
        // when
        OperationMetadata result = OperationMetadata.builder()
                .conflictHandleStrategy(ConflictHandleStrategy.REJECT)
                .conflictHandlerClass(null)
                .build();

        // then
        assertThat(result.getConflictHandlerClass()).isNull();
    }

    private static class TestConflictHandler implements ConflictHandler {
        @Override
        public <T> Optional<T> handle(UUID idempotencyKey, Class<T> c) {
            return Optional.empty();
        }

        @Override
        public ConflictHandleStrategy getStrategy() {
            return null;
        }
    }

    private static class TestFingerprintPolicy implements FingerprintPolicy {

        @Override
        public String generate(RequestContext context) {
            return "fingerprint";
        }

        @Override
        public boolean compare(String previous, String current) {
            return previous.equals(current);
        }

        @Override
        public void handle(FingerprintMismatchContext context) {
        }
    }
}