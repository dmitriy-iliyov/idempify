package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultOperationMapperUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = TestClock.EPOCH;

    private final DefaultOperationMapper tested = new DefaultOperationMapper();

    @Test
    @DisplayName("UT toOperation() should start the operation in process on its first attempt with no result yet")
    void toOperation_shouldStartOperationInProcessOnFirstAttemptWithNoResultYet() {
        // when
        Operation result = tested.toOperation(KEY, "fingerprint", metadata(Duration.ofHours(24)), NOW);

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.getResult()).isNull();
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UT toOperation() should set the expiry a ttl away from the given timestamp")
    void toOperation_shouldSetExpiryTtlAwayFromGivenTimestamp() {
        // when
        Operation result = tested.toOperation(KEY, "fingerprint", metadata(Duration.ofMinutes(30)), NOW);

        // then
        assertThat(result.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("UT toOperation() when there is no fingerprint should leave it unset")
    void toOperation_whenThereIsNoFingerprint_shouldLeaveItUnset() {
        // when
        Operation result = tested.toOperation(KEY, null, metadata(Duration.ofHours(24)), NOW);

        // then
        assertThat(result.getFingerprint()).isNull();
    }

    private OperationMetadata metadata(Duration ttl) {
        return TestOperationMetadata.builder().ttl(ttl).build();
    }
}
