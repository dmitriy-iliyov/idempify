package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A carrier, but not a trivial one: its expiry is what both caches count their TTL from, and its
 * {@code equals} is hand-written, so what is judged is that a record still waiting for its answer is
 * representable and that two records carrying the same values count as one.
 */
class DefaultRawResponseContainerUnitTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    @DisplayName("UT getters() should return what the container was built from")
    void getters_shouldReturnWhatContainerWasBuiltFrom() {
        // when
        RawResponseContainer tested = answered();

        // then
        assertThat(tested.getResponse()).isEqualTo("raw-response");
        assertThat(tested.getFingerprint()).isEqualTo("fingerprint");
        assertThat(tested.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("UT constructor() when the operation has not answered yet should accept an empty response and expiry")
    void constructor_whenOperationHasNotAnsweredYet_shouldAcceptEmptyResponseAndExpiry() {
        // when
        RawResponseContainer tested = new DefaultRawResponseContainer(null, "fingerprint", null);

        // then
        assertThat(tested.getResponse()).isNull();
        assertThat(tested.getExpiresAt()).isNull();
        assertThat(tested.getFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    @DisplayName("UT equals() when every value matches should be equal and share a hash code")
    void equals_whenEveryValueMatches_shouldBeEqualAndShareHashCode() {
        // when / then
        assertThat(answered()).isEqualTo(answered());
        assertThat(answered()).hasSameHashCodeAs(answered());
    }

    @Test
    @DisplayName("UT equals() when the expiry differs should not be equal")
    void equals_whenExpiryDiffers_shouldNotBeEqual() {
        // when / then
        assertThat(answered()).isNotEqualTo(
                new DefaultRawResponseContainer("raw-response", "fingerprint", EXPIRES_AT.plusSeconds(1)));
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        // when / then
        assertThat(answered()).isNotEqualTo("raw-response");
    }

    @Test
    @DisplayName("UT toString() should say whether a response is there without printing it")
    void toString_shouldSayWhetherResponseIsThereWithoutPrintingIt() {
        // when / then
        assertThat(answered().toString()).contains("hasResponse=true", "fingerprint='fingerprint'");
        assertThat(new DefaultRawResponseContainer(null, "fingerprint", null).toString())
                .contains("hasResponse=false");
    }

    private static DefaultRawResponseContainer answered() {
        return new DefaultRawResponseContainer("raw-response", "fingerprint", EXPIRES_AT);
    }
}
