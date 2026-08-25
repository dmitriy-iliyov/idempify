package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultOperationContextUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final ExternalOperationCallback<String> callback = () -> "result";

    @Test
    @DisplayName("UT constructor when operationResultType is null should throw NullPointerException")
    void constructor_whenOperationResultTypeIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationContext<>(null, callback, KEY, "fingerprint"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operationResultType cannot be null");
    }

    @Test
    @DisplayName("UT constructor when operationCallback is null should throw NullPointerException")
    void constructor_whenOperationCallbackIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationContext<String>(String.class, null, KEY, "fingerprint"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operationCallback cannot be null");
    }

    @Test
    @DisplayName("UT constructor when idempotencyKey is null should throw NullPointerException")
    void constructor_whenIdempotencyKeyIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationContext<>(String.class, callback, null, "fingerprint"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idempotencyKey cannot be null");
    }

    @Test
    @DisplayName("UT getters should hand back everything the context was built with")
    void getters_shouldHandBackEverythingContextWasBuiltWith() throws Throwable {
        // given
        DefaultOperationContext<String> tested = new DefaultOperationContext<>(String.class, callback, KEY, "fingerprint");

        // then
        assertThat(tested.getOperationResultType()).isEqualTo(String.class);
        assertThat(tested.getOperationCallback().call()).isEqualTo("result");
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(tested.getFingerprint()).contains("fingerprint");
    }

    @Test
    @DisplayName("UT getFingerprint() when there is no fingerprint should be empty")
    void getFingerprint_whenThereIsNoFingerprint_shouldBeEmpty() {
        // given
        DefaultOperationContext<String> tested = new DefaultOperationContext<>(String.class, callback, KEY, null);

        // then
        assertThat(tested.getFingerprint()).isEmpty();
    }

    @Test
    @DisplayName("UT equals() when two contexts describe the same call should treat them as equal")
    void equals_whenTwoContextsDescribeSameCall_shouldTreatThemAsEqual() {
        // given
        DefaultOperationContext<String> one = new DefaultOperationContext<>(String.class, callback, KEY, "fingerprint");
        DefaultOperationContext<String> other = new DefaultOperationContext<>(String.class, callback, KEY, "fingerprint");

        // then
        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when the fingerprint differs should treat the contexts as different")
    void equals_whenFingerprintDiffers_shouldTreatContextsAsDifferent() {
        // given
        DefaultOperationContext<String> one = new DefaultOperationContext<>(String.class, callback, KEY, "one");
        DefaultOperationContext<String> other = new DefaultOperationContext<>(String.class, callback, KEY, "other");

        // then
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT equals() when compared to another type should not be equal")
    void equals_whenComparedToAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultOperationContext<>(String.class, callback, KEY, "fingerprint"))
                .isNotEqualTo("not a context");
    }

    @Test
    @DisplayName("UT toString() should name the result type, the key and the fingerprint")
    void toString_shouldNameResultTypeKeyAndFingerprint() {
        // when
        String result = new DefaultOperationContext<>(String.class, callback, KEY, "fingerprint").toString();

        // then
        assertThat(result).contains("java.lang.String", KEY.toString(), "fingerprint='fingerprint'");
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        DefaultOperationContext<String> tested = new DefaultOperationContext<>(
                String.class, () -> "x", java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "fp");
        assertThat(tested).isEqualTo(tested);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        DefaultOperationContext<String> tested = new DefaultOperationContext<>(
                String.class, () -> "x", java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "fp");
        assertThat(tested).isNotEqualTo("not a context");
    }

    @Test
    @DisplayName("UT equals() when the keys differ should tell the contexts apart")
    void equals_whenKeysDiffer_shouldTellContextsApart() {
        io.github.dmitriyiliyov.idempify.core.ExternalOperationCallback<String> callback = () -> "x";
        DefaultOperationContext<String> one = new DefaultOperationContext<>(
                String.class, callback, java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "fp");
        DefaultOperationContext<String> other = new DefaultOperationContext<>(
                String.class, callback, java.util.UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), "fp");

        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT equals() when the fingerprints differ should tell the contexts apart")
    void equals_whenFingerprintsDiffer_shouldTellContextsApart() {
        io.github.dmitriyiliyov.idempify.core.ExternalOperationCallback<String> callback = () -> "x";
        java.util.UUID key = java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        DefaultOperationContext<String> one = new DefaultOperationContext<>(String.class, callback, key, "one");
        DefaultOperationContext<String> other = new DefaultOperationContext<>(String.class, callback, key, "other");

        assertThat(one).isNotEqualTo(other);
    }
}
