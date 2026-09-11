package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A record, so its accessors are the compiler's business. What is worth holding down is what the row is
 * allowed to look like - a claim leaves three columns empty - and that two rows carrying the same values
 * count as the same row, which is how every store test compares what it wrote with what it read.
 */
class RawOperationUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    @DisplayName("UT constructor() when the row is a claim should accept it with result, response and expiry empty")
    void constructor_whenRowIsClaim_shouldAcceptItWithResultResponseAndExpiryEmpty() {
        // when
        RawOperation tested = new RawOperation(
                KEY, OperationStatus.IN_PROCESS, true, null, null, "fingerprint", null, NOW);

        // then
        assertThat(tested.result()).isNull();
        assertThat(tested.response()).isNull();
        assertThat(tested.expiresAt()).isNull();
    }

    @Test
    @DisplayName("UT equals() when every column matches should be equal and share a hash code")
    void equals_whenEveryColumnMatches_shouldBeEqualAndShareHashCode() {
        // when / then
        assertThat(processed()).isEqualTo(processed());
        assertThat(processed()).hasSameHashCodeAs(processed());
    }

    @Test
    @DisplayName("UT equals() when one column differs should not be equal")
    void equals_whenOneColumnDiffers_shouldNotBeEqual() {
        // when / then
        assertThat(processed()).isNotEqualTo(new RawOperation(
                KEY, OperationStatus.PROCESSED, true, "other-result", "raw-response", "fingerprint",
                EXPIRES_AT, NOW));
    }

    private static RawOperation processed() {
        return new RawOperation(
                KEY, OperationStatus.PROCESSED, true, "raw-result", "raw-response", "fingerprint",
                EXPIRES_AT, NOW);
    }
}
