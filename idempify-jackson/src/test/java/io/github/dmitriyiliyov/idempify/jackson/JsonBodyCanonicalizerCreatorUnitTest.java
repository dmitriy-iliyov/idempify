package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizer;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A creator rather than a bean because a canonicalizer depends on the settings of the call site it serves, so
 * one instance cannot serve them all. What is judged here is that it says which format it answers for and
 * builds a fresh canonicalizer per config.
 */
class JsonBodyCanonicalizerCreatorUnitTest {

    private final JsonBodyCanonicalizerCreator tested = new JsonBodyCanonicalizerCreator(new ObjectMapper());

    @Test
    @DisplayName("UT constructor() when the mapper is null should throw NullPointerException")
    void constructor_whenMapperIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new JsonBodyCanonicalizerCreator(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("objectMapper cannot be null");
    }

    @Test
    @DisplayName("UT getFormat() should answer for JSON, which is what puts it in the dispatcher")
    void getFormat_shouldAnswerForJsonWhichIsWhatPutsItInDispatcher() {
        // when / then
        assertThat(tested.getFormat()).isEqualTo(BodyFormat.JSON);
    }

    @Test
    @DisplayName("UT create() should build a JSON canonicalizer for the config it was given")
    void create_shouldBuildJsonCanonicalizerForConfigItWasGiven() {
        // when
        BodyCanonicalizer result = tested.create(BodyCanonicalizerConfig.defaults());

        // then
        assertThat(result).isInstanceOf(JsonBodyCanonicalizer.class);
    }

    @Test
    @DisplayName("UT create() when called twice should build a canonicalizer per call site rather than share one")
    void create_whenCalledTwice_shouldBuildCanonicalizerPerCallSiteRatherThanShareOne() {
        // when / then
        assertThat(tested.create(BodyCanonicalizerConfig.defaults()))
                .isNotSameAs(tested.create(BodyCanonicalizerConfig.defaults()));
    }
}
