package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.aop.IdempifyAopAutoConfiguration;
import io.github.dmitriyiliyov.idempify.core.Idempotent;
import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.response.ResponseManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.RequestPath;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The endpoint registry judging, at startup, the controllers of a real MVC context against the entry
 * {@code idempify-aop} brings: an endpoint the library would leave unprotected refuses to start instead of
 * running without protection.
 * <p>
 * The entry is the real one, so its answer is read off the proxy Boot's own auto-proxying builds, not off the
 * configuration. What belongs to the core only answers.
 */
class IdempifyHttpStartupComponentTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    IdempifyAopAutoConfiguration.class,
                    IdempifyHttpAutoConfiguration.class
            ))
            .withUserConfiguration(WebMvcConfiguration.class, CoreConfiguration.class);

    @Test
    @DisplayName("CT application when the idempotent endpoint is intercepted by the aspect should start and index it")
    void application_whenIdempotentEndpointIsInterceptedByAspect_shouldStartAndIndexIt() {
        contextRunner
                .withUserConfiguration(ProtectedControllerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IdempotentHandlerRegistry.class)
                            .getHandlerMethod("POST", RequestPath.parse("/payments", null)))
                            .isNotNull();
                });
    }

    @Test
    @DisplayName("CT application when an idempotent endpoint serves GET should refuse to start naming the endpoint and the method")
    void application_whenIdempotentEndpointServesGet_shouldRefuseToStartNamingEndpointAndMethod() {
        contextRunner
                .withUserConfiguration(ReadingControllerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("/payments/latest")
                            .hasMessageContaining("HTTP method 'GET' is not supported");
                });
    }

    @Test
    @DisplayName("CT application when aspect proxying is switched off should refuse to start naming the unprotected endpoint")
    void application_whenAspectProxyingIsSwitchedOff_shouldRefuseToStartNamingUnprotectedEndpoint() {
        contextRunner
                .withUserConfiguration(ProtectedControllerConfiguration.class)
                .withPropertyValues("spring.aop.auto=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("/payments")
                            .hasMessageContaining("no entry intercepts")
                            .hasMessageContaining("spring.aop.auto");
                });
    }

    @Test
    @DisplayName("CT application when the idempotent handler method is private should refuse to start naming the unprotected endpoint")
    void application_whenIdempotentHandlerMethodIsPrivate_shouldRefuseToStartNamingUnprotectedEndpoint() {
        contextRunner
                .withUserConfiguration(PrivateHandlerControllerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("/payments/private")
                            .hasMessageContaining("no entry intercepts");
                });
    }

    @Test
    @DisplayName("CT application when the idempotent handler method is final under a class proxy should refuse to start naming the unprotected endpoint")
    void application_whenIdempotentHandlerMethodIsFinalUnderClassProxy_shouldRefuseToStartNamingUnprotectedEndpoint() {
        contextRunner
                .withUserConfiguration(FinalHandlerControllerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("/payments/final")
                            .hasMessageContaining("no entry intercepts");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class WebMvcConfiguration { }

    @Configuration(proxyBeanMethods = false)
    static class CoreConfiguration {

        @Bean
        public OperationMetadataResolver operationMetadataResolver() {
            return mock(OperationMetadataResolver.class);
        }

        @Bean
        public ResponseManager responseManager() {
            return mock(ResponseManager.class);
        }

        @Bean
        public FingerprintMatcher fingerprintMatcher() {
            return mock(FingerprintMatcher.class);
        }

        @Bean
        public IdempotentProcessor idempotentProcessor() {
            return mock(IdempotentProcessor.class);
        }

        @Bean
        public Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProtectedControllerConfiguration {

        @Bean
        public ProtectedController protectedController() {
            return new ProtectedController();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ReadingControllerConfiguration {

        @Bean
        public ReadingController readingController() {
            return new ReadingController();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrivateHandlerControllerConfiguration {

        @Bean
        public PrivateHandlerController privateHandlerController() {
            return new PrivateHandlerController();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FinalHandlerControllerConfiguration {

        @Bean
        public FinalHandlerController finalHandlerController() {
            return new FinalHandlerController();
        }
    }

    @RestController
    static class ProtectedController {

        @Idempotent
        @PostMapping("/payments")
        public String pay() {
            return "paid";
        }
    }

    @RestController
    static class ReadingController {

        @Idempotent
        @GetMapping("/payments/latest")
        public String latest() {
            return "latest";
        }
    }

    @RestController
    static class PrivateHandlerController {

        @Idempotent
        @PostMapping("/payments/private")
        private String pay() {
            return "paid";
        }
    }

    @RestController
    static class FinalHandlerController {

        @Idempotent
        @PostMapping("/payments/final")
        public final String pay() {
            return "paid";
        }
    }
}
