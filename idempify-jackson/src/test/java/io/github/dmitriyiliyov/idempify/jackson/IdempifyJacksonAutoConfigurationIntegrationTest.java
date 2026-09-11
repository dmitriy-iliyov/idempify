package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizerCreator;
import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyJacksonAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyJacksonAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

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

    @Test
    @DisplayName("IT context when idempify is switched off should register nothing at all")
    void context_whenIdempifyIsSwitchedOff_shouldRegisterNothingAtAll() {
        contextRunner
                .withPropertyValues("idempify.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ResultSerializer.class);
                    assertThat(context).doesNotHaveBean(ResultDeserializer.class);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched on by hand should register the module all the same")
    void context_whenIdempifyIsSwitchedOnByHand_shouldRegisterModuleAllTheSame() {
        contextRunner
                .withPropertyValues("idempify.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JacksonResultSerializer.class);
                    assertThat(context).hasSingleBean(JacksonResultDeserializer.class);
                });
    }

    @Test
    @DisplayName("IT context when nothing else answers should register every bean the module contributes")
    void context_whenNothingElseAnswers_shouldRegisterEveryBeanModuleContributes() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ResponseSerializer.class);
            assertThat(context).hasSingleBean(ResponseDeserializer.class);
            assertThat(context).hasSingleBean(BodyCanonicalizerCreator.class);
            assertThat(context).hasSingleBean(JsonBodyCanonicalizerCreator.class);
        });
    }

    @Test
    @DisplayName("IT context should keep the generic wrappers out of the container")
    void context_shouldKeepGenericWrappersOutOfContainer() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(GenericJacksonSerializer.class);
            assertThat(context).doesNotHaveBean(GenericJacksonDeserializer.class);
        });
    }

    @Test
    @DisplayName("IT context when an existing ResponseSerializer bean should not register the module's own")
    void context_whenExistingResponseSerializerBean_shouldNotRegisterModulesOwn() {
        ResponseSerializer own = response -> "own";
        contextRunner
                .withBean(ResponseSerializer.class, () -> own)
                .run(context -> assertThat(context.getBean(ResponseSerializer.class)).isSameAs(own));
    }

    @Test
    @DisplayName("IT context when an existing ResponseDeserializer bean should not register the module's own")
    void context_whenExistingResponseDeserializerBean_shouldNotRegisterModulesOwn() {
        ResponseDeserializer own = rawResponse -> null;
        contextRunner
                .withBean(ResponseDeserializer.class, () -> own)
                .run(context -> assertThat(context.getBean(ResponseDeserializer.class)).isSameAs(own));
    }

    @Test
    @DisplayName("IT context when an existing canonicalizer creator exists should not register the JSON one")
    void context_whenExistingCanonicalizerCreatorExists_shouldNotRegisterJsonOne() {
        contextRunner
                .withBean(JsonBodyCanonicalizerCreator.class, () -> new JsonBodyCanonicalizerCreator(new ObjectMapper()))
                .run(context -> assertThat(context).hasSingleBean(JsonBodyCanonicalizerCreator.class));
    }

    @Test
    @DisplayName("IT the registered ResponseSerializer should render a response through the module's own mapper")
    void registeredResponseSerializer_shouldRenderResponseThroughModulesOwnMapper() {
        contextRunner.run(context -> {
            String raw = context.getBean(ResponseSerializer.class)
                    .serialize(new DefaultResponse(201, new byte [0], "application/json", Map.of()));

            assertThat(raw).contains("\"status\":201");
        });
    }

    @Test
    @DisplayName("IT the registered response pair should close the circle on a mapper Boot did not assemble")
    void registeredResponsePair_shouldCloseCircleOnMapperBootDidNotAssemble() {
        contextRunner.run(context -> {
            String raw = context.getBean(ResponseSerializer.class).serialize(
                    new DefaultResponse(201, new byte [] {1, 2}, "application/json", Map.of("Location", "/1"))
            );

            Response result = context.getBean(ResponseDeserializer.class).deserialize(raw);

            assertThat(result.getStatus()).isEqualTo(201);
            assertThat(result.getBody()).isEqualTo(new byte [] {1, 2});
            assertThat(result.getContentType()).isEqualTo("application/json");
            assertThat(result.getHeaders()).containsEntry("Location", "/1");
        });
    }
}
