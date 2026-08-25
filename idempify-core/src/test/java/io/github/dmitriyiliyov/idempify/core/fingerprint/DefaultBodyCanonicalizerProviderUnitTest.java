package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Written from the contract of {@link BodyCanonicalizerProvider}: it hands out the canonicalizer for the
 * format a config names, and a format nobody can read has to be reported rather than guessed at. Which
 * formats an application needs depends on its endpoints, so a missing creator is only an error once a call
 * site asks for that format.
 */
class DefaultBodyCanonicalizerProviderUnitTest {

    @Test
    @DisplayName("UT constructor when creators is null should throw NullPointerException")
    void constructor_whenCreatorsIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultBodyCanonicalizerProvider(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("creators cannot be null");
    }

    @Test
    @DisplayName("UT constructor when no creator is registered should say which kind of bean is missing")
    void constructor_whenNoCreatorIsRegistered_shouldSayWhichKindOfBeanIsMissing() {
        assertThatThrownBy(() -> new DefaultBodyCanonicalizerProvider(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BodyCanonicalizerCreator");
    }

    @Test
    @DisplayName("UT constructor when two creators claim the same format should name the format and both classes")
    void constructor_whenTwoCreatorsClaimSameFormat_shouldNameFormatAndBothClasses() {
        // given
        TestCreator first = new TestCreator(BodyFormat.JSON);
        TestCreator second = new TestCreator(BodyFormat.JSON);

        // when / then
        assertThatThrownBy(() -> new DefaultBodyCanonicalizerProvider(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BodyFormat.JSON.name())
                .hasMessageContaining(TestCreator.class.getName());
    }

    @Test
    @DisplayName("UT provide() when a creator serves the format should build the canonicalizer from the given config")
    void provide_whenCreatorServesFormat_shouldBuildCanonicalizerFromGivenConfig() {
        // given
        TestCreator creator = new TestCreator(BodyFormat.JSON);
        DefaultBodyCanonicalizerProvider tested = new DefaultBodyCanonicalizerProvider(List.of(creator));
        BodyCanonicalizerConfig config = BodyCanonicalizerConfig.builder().format(BodyFormat.JSON).build();

        // when
        BodyCanonicalizer result = tested.provide(config);

        // then
        assertThat(result).isNotNull();
        assertThat(creator.lastConfig).isSameAs(config);
    }

    @Test
    @DisplayName("UT provide() when no creator serves the format should name it and list the ones registered")
    void provide_whenNoCreatorServesFormat_shouldNameItAndListOnesRegistered() {
        // given
        DefaultBodyCanonicalizerProvider tested = new DefaultBodyCanonicalizerProvider(
                List.of(new TestCreator(BodyFormat.JSON)));
        BodyCanonicalizerConfig config = BodyCanonicalizerConfig.builder().format(BodyFormat.XML).build();

        // when / then
        assertThatThrownBy(() -> tested.provide(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BodyFormat.XML.name())
                .hasMessageContaining(BodyFormat.JSON.name());
    }

    @Test
    @DisplayName("UT provide() when the config is null should throw NullPointerException")
    void provide_whenConfigIsNull_shouldThrowNullPointerException() {
        // given
        DefaultBodyCanonicalizerProvider tested = new DefaultBodyCanonicalizerProvider(
                List.of(new TestCreator(BodyFormat.JSON)));

        // when / then
        assertThatThrownBy(() -> tested.provide(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config cannot be null");
    }

    @Test
    @DisplayName("UT provide() when several formats are registered should pick the one the config names")
    void provide_whenSeveralFormatsAreRegistered_shouldPickOneConfigNames() {
        // given
        TestCreator json = new TestCreator(BodyFormat.JSON);
        TestCreator xml = new TestCreator(BodyFormat.XML);
        DefaultBodyCanonicalizerProvider tested = new DefaultBodyCanonicalizerProvider(List.of(json, xml));

        // when
        tested.provide(BodyCanonicalizerConfig.builder().format(BodyFormat.XML).build());

        // then
        assertThat(xml.lastConfig).isNotNull();
        assertThat(json.lastConfig).isNull();
    }

    private static final class TestCreator implements BodyCanonicalizerCreator {

        private final BodyFormat format;
        private BodyCanonicalizerConfig lastConfig;

        private TestCreator(BodyFormat format) {
            this.format = format;
        }

        @Override
        public BodyCanonicalizer create(BodyCanonicalizerConfig config) {
            lastConfig = config;
            return body -> new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public BodyFormat getFormat() {
            return format;
        }
    }
}
