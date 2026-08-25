package io.github.dmitriyiliyov.idempify.core.fingerprint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFingerprintMismatchContextUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    @DisplayName("UT getters should hand back the key and both fingerprints")
    void getters_shouldHandBackKeyAndBothFingerprints() {
        // given
        DefaultFingerprintMismatchContext tested = new DefaultFingerprintMismatchContext(KEY, "previous", "current");

        // then
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(tested.getPreviousFingerprint()).isEqualTo("previous");
        assertThat(tested.getCurrentFingerprint()).isEqualTo("current");
    }

    @Test
    @DisplayName("UT equals() when two contexts describe the same mismatch should treat them as equal")
    void equals_whenTwoContextsDescribeSameMismatch_shouldTreatThemAsEqual() {
        // given
        DefaultFingerprintMismatchContext one = new DefaultFingerprintMismatchContext(KEY, "previous", "current");
        DefaultFingerprintMismatchContext other = new DefaultFingerprintMismatchContext(KEY, "previous", "current");

        // then
        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when the key differs should treat the contexts as different")
    void equals_whenKeyDiffers_shouldTreatContextsAsDifferent() {
        assertThat(new DefaultFingerprintMismatchContext(KEY, "previous", "current"))
                .isNotEqualTo(new DefaultFingerprintMismatchContext(OTHER_KEY, "previous", "current"));
    }

    @Test
    @DisplayName("UT equals() when compared to another type should not be equal")
    void equals_whenComparedToAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultFingerprintMismatchContext(KEY, "previous", "current")).isNotEqualTo("not a context");
    }

    @Test
    @DisplayName("UT toString() should name the key and both fingerprints")
    void toString_shouldNameKeyAndBothFingerprints() {
        assertThat(new DefaultFingerprintMismatchContext(KEY, "previous", "current").toString())
                .contains(KEY.toString(), "previousFingerprint='previous'", "currentFingerprint='current'");
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        DefaultFingerprintMismatchContext tested = new DefaultFingerprintMismatchContext(java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "a", "b");
        assertThat(tested).isEqualTo(tested);
    }

    @Test
    @DisplayName("UT equals() when compared with null should not be equal")
    void equals_whenComparedWithNull_shouldNotBeEqual() {
        assertThat(new DefaultFingerprintMismatchContext(java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "a", "b")).isNotEqualTo(null);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultFingerprintMismatchContext(java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "a", "b")).isNotEqualTo("not a config");
    }

    @Test
    @DisplayName("UT hashCode() when two are equal should agree")
    void hashCode_whenTwoAreEqual_shouldAgree() {
        assertThat(new DefaultFingerprintMismatchContext(java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "a", "b")).hasSameHashCodeAs(new DefaultFingerprintMismatchContext(java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "a", "b"));
    }

    @Test
    @DisplayName("UT equals() when the stored fingerprints differ should tell the contexts apart")
    void equals_whenStoredFingerprintsDiffer_shouldTellContextsApart() {
        java.util.UUID key = java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        assertThat(new DefaultFingerprintMismatchContext(key, "one", "current"))
                .isNotEqualTo(new DefaultFingerprintMismatchContext(key, "other", "current"));
    }
}
