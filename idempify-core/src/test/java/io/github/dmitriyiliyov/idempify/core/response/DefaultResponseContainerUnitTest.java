package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a replay is handed once the record has been read back. It guards nothing on the way in, and that is
 * the point worth holding down: an empty fingerprint is legal here, because fingerprinting is off for most
 * call sites.
 */
class DefaultResponseContainerUnitTest {

    @Test
    @DisplayName("UT getters() should return what the container was built from")
    void getters_shouldReturnWhatContainerWasBuiltFrom() {
        // given
        Response response = response();

        // when
        ResponseContainer tested = new DefaultResponseContainer(response, "fingerprint");

        // then
        assertThat(tested.getResponse()).isSameAs(response);
        assertThat(tested.getFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    @DisplayName("UT constructor() when fingerprinting is off should accept a container without a fingerprint")
    void constructor_whenFingerprintingIsOff_shouldAcceptContainerWithoutFingerprint() {
        // when
        ResponseContainer tested = new DefaultResponseContainer(response(), null);

        // then
        assertThat(tested.getFingerprint()).isNull();
        assertThat(tested.getResponse()).isNotNull();
    }

    @Test
    @DisplayName("UT toString() should name both parts")
    void toString_shouldNameBothParts() {
        // when / then
        assertThat(new DefaultResponseContainer(response(), "fingerprint").toString())
                .contains("response=", "fingerprint='fingerprint'");
    }

    private static Response response() {
        return new DefaultResponse(201, new byte [] {1}, "application/json", Map.of());
    }
}
