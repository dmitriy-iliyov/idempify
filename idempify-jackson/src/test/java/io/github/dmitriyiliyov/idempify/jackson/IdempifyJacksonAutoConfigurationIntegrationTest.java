package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.ResultSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyJacksonAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyJacksonAutoConfiguration.class))
            .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class));

    @Test
    @DisplayName("IT context when no existing Jackson beans should register JacksonResultSerializer and JacksonResultDeserializer")
    void context_whenNoExistingJacksonBeans_shouldRegisterSerializerAndDeserializer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ResultSerializer.class);
            assertThat(context).hasSingleBean(JacksonResultSerializer.class);

            assertThat(context).hasSingleBean(ResultDeserializer.class);
            assertThat(context).hasSingleBean(JacksonResultDeserializer.class);
        });
    }

    @Test
    @DisplayName("IT context when existing ResultSerializer bean should not register JacksonResultSerializer")
    void context_whenExistingResultSerializerBean_shouldNotRegisterJacksonResultSerializer() {
        contextRunner
                .withBean(ResultSerializer.class, () -> mock(ResultSerializer.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ResultSerializer.class);
                    assertThat(context).doesNotHaveBean(JacksonResultSerializer.class);

                    assertThat(context).hasSingleBean(ResultDeserializer.class);
                    assertThat(context).hasSingleBean(JacksonResultDeserializer.class);
                });
    }

    @Test
    @DisplayName("IT context when existing ResultDeserializer bean should not register JacksonResultDeserializer")
    void context_whenExistingResultDeserializerBean_shouldNotRegisterJacksonResultDeserializer() {
        contextRunner
                .withBean(ResultDeserializer.class, () -> mock(ResultDeserializer.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ResultDeserializer.class);
                    assertThat(context).doesNotHaveBean(JacksonResultDeserializer.class);

                    assertThat(context).hasSingleBean(ResultSerializer.class);
                    assertThat(context).hasSingleBean(JacksonResultSerializer.class);
                });
    }
}