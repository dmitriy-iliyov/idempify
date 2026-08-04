package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class IdempifyAopComponentTest {

    private static final String HEADER_NAME = "X-Payment-Key";
    private static final UUID HEADER_KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
            .withUserConfiguration(AopProxyConfiguration.class)
            .withBean(RecordingIdempotentProcessor.class, RecordingIdempotentProcessor::new)
            .withBean(RecordingOperationMetadataManager.class, RecordingOperationMetadataManager::new)
            .withBean(KeyExtractor.class, HeaderKeyExtractor::new)
            .withBean(RequestContextProvider.class, () -> () -> new TestRequestContext(HEADER_NAME, HEADER_KEY.toString()))
            .withBean(PaymentService.class, PaymentService::new);

    @Test
    @DisplayName("CT call when the operation is a first attempt should run the method and return its result")
    void call_whenOperationIsFirstAttempt_shouldRunMethodAndReturnItsResult() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);

            // when
            String result = service.pay("10");

            // then
            assertThat(result).isEqualTo("paid:10");
            assertThat(service.payCalls()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("CT call when the core replays a stored result should return it without running the method")
    void call_whenCoreReplaysStoredResult_shouldReturnItWithoutRunningMethod() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            context.getBean(RecordingIdempotentProcessor.class).replay = "paid:replayed";

            // when
            String result = service.pay("10");

            // then
            assertThat(result).isEqualTo("paid:replayed");
            assertThat(service.payCalls()).isZero();
        });
    }

    @Test
    @DisplayName("CT call when the annotation names no key should take it from the request header")
    void call_whenAnnotationNamesNoKey_shouldTakeItFromRequestHeader() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            RecordingIdempotentProcessor processor = context.getBean(RecordingIdempotentProcessor.class);

            // when
            service.pay("10");

            // then
            assertThat(processor.capturedContext.getIdempotencyKey()).isEqualTo(HEADER_KEY);
        });
    }

    @Test
    @DisplayName("CT call when the annotation names a key should take it from the arguments instead of the header")
    void call_whenAnnotationNamesKey_shouldTakeItFromArgumentsInsteadOfHeader() {
        contextRunner.run(context -> {
            // given
            UUID key = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            PaymentService service = context.getBean(PaymentService.class);
            RecordingIdempotentProcessor processor = context.getBean(RecordingIdempotentProcessor.class);

            // when
            service.payWithKey(new PaymentRequest(key.toString()));

            // then
            assertThat(processor.capturedContext.getIdempotencyKey()).isEqualTo(key);
        });
    }

    @Test
    @DisplayName("CT call when the resolved metadata uses fingerprint should reach the core with one generated from the request")
    void call_whenResolvedMetadataUsesFingerprint_shouldReachCoreWithOneGeneratedFromRequest() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            RecordingIdempotentProcessor processor = context.getBean(RecordingIdempotentProcessor.class);
            RecordingOperationMetadataManager manager = context.getBean(RecordingOperationMetadataManager.class);

            // when
            service.pay("10");

            // then
            assertThat(manager.fingerprintPolicy.generateCalls).isEqualTo(1);
            assertThat(processor.capturedContext.getFingerprint()).contains(RecordingFingerprintPolicy.FINGERPRINT);
        });
    }

    @Test
    @DisplayName("CT call when the resolved metadata does not use fingerprint should reach the core with an empty one")
    void call_whenResolvedMetadataDoesNotUseFingerprint_shouldReachCoreWithEmptyOne() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            RecordingIdempotentProcessor processor = context.getBean(RecordingIdempotentProcessor.class);
            RecordingOperationMetadataManager manager = context.getBean(RecordingOperationMetadataManager.class);
            manager.useFingerprint = false;

            // when
            service.pay("10");

            // then
            assertThat(processor.capturedContext.getFingerprint()).isEmpty();
            assertThat(manager.fingerprintPolicy.generateCalls).isZero();
        });
    }

    @Test
    @DisplayName("CT call when the method is annotated should hand every annotation attribute to the metadata manager")
    void call_whenMethodIsAnnotated_shouldHandEveryAnnotationAttributeToMetadataManager() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            RecordingOperationMetadataManager manager = context.getBean(RecordingOperationMetadataManager.class);

            // when
            service.pay("10");

            // then
            RawOperationMetadata raw = manager.capturedRawMetadata;
            assertThat(manager.capturedConfigName).isEqualTo("payments");
            assertThat(raw.getHeaderName()).isEqualTo(HEADER_NAME);
            assertThat(raw.getTtl()).isEqualTo(Duration.ofHours(48));
            assertThat(raw.getConflictHandleStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
            assertThat(raw.getFingerprintToggle()).isEqualTo(Toggle.ENABLE);
            assertThat(raw.getCacheToggle()).isEqualTo(Toggle.ENABLE);
            assertThat(raw.getCache4xxToggle()).isEqualTo(Toggle.DISABLE);
            assertThat(raw.getCache5xxToggle()).isEqualTo(Toggle.DISABLE);
        });
    }

    @Test
    @DisplayName("CT call when the resolved metadata is produced should be the one the core is given")
    void call_whenResolvedMetadataIsProduced_shouldBeTheOneCoreIsGiven() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            RecordingIdempotentProcessor processor = context.getBean(RecordingIdempotentProcessor.class);
            RecordingOperationMetadataManager manager = context.getBean(RecordingOperationMetadataManager.class);

            // when
            service.pay("10");

            // then
            assertThat(processor.capturedMetadata).isSameAs(manager.produced);
        });
    }

    @Test
    @DisplayName("CT call when the same method is called twice should resolve its metadata only once")
    void call_whenSameMethodIsCalledTwice_shouldResolveItsMetadataOnlyOnce() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            RecordingOperationMetadataManager manager = context.getBean(RecordingOperationMetadataManager.class);

            // when
            service.pay("10");
            service.pay("20");

            // then
            assertThat(manager.mergeCalls).isEqualTo(1);
            assertThat(service.payCalls()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("CT call when different methods are called should resolve metadata for each of them")
    void call_whenDifferentMethodsAreCalled_shouldResolveMetadataForEachOfThem() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            RecordingOperationMetadataManager manager = context.getBean(RecordingOperationMetadataManager.class);

            // when
            service.pay("10");
            service.payWithKey(new PaymentRequest(UUID.randomUUID().toString()));

            // then
            assertThat(manager.mergeCalls).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("CT call when the method carries no annotation should not be intercepted")
    void call_whenMethodCarriesNoAnnotation_shouldNotBeIntercepted() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);
            RecordingIdempotentProcessor processor = context.getBean(RecordingIdempotentProcessor.class);

            // when
            String result = service.refund("10");

            // then
            assertThat(result).isEqualTo("refunded:10");
            assertThat(processor.processCalls).isZero();
        });
    }

    @Test
    @DisplayName("CT call when the intercepted method throws should let the exception reach the caller")
    void call_whenInterceptedMethodThrows_shouldLetExceptionReachCaller() {
        contextRunner.run(context -> {
            // given
            PaymentService service = context.getBean(PaymentService.class);

            // when // then
            assertThatThrownBy(() -> service.failingPay())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("boom");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AopProxyConfiguration { }

    public static class PaymentService {

        private int payCalls;

        /**
         * Read through the proxy on purpose: a CGLIB subclass carries its own copy of the field, so only a
         * call reaches the target instance the aspect actually advises.
         */
        public int payCalls() {
            return payCalls;
        }

        @Idempotent(
                config = "payments",
                headerName = HEADER_NAME,
                ttl = 48,
                timeUnit = TimeUnit.HOURS,
                onConflict = ConflictHandleStrategy.WAIT,
                useFingerprint = Toggle.ENABLE,
                shouldCache = Toggle.ENABLE,
                shouldCache4xx = Toggle.DISABLE,
                shouldCache5xx = Toggle.DISABLE
        )
        public String pay(String amount) {
            payCalls++;
            return "paid:" + amount;
        }

        @Idempotent(idempotencyKey = "#request.id")
        public String payWithKey(PaymentRequest request) {
            return "paid:" + request.getId();
        }

        @Idempotent
        public String failingPay() {
            throw new IllegalStateException("boom");
        }

        public String refund(String amount) {
            return "refunded:" + amount;
        }
    }

    public static class PaymentRequest {

        private final String id;

        public PaymentRequest(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    /**
     * Stands in for the core: runs the caller's operation unless a stored result is there to be replayed.
     */
    static class RecordingIdempotentProcessor implements IdempotentProcessor {

        OperationContext<?> capturedContext;
        OperationMetadata capturedMetadata;
        Object replay;
        int processCalls;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T process(OperationContext<T> context, OperationMetadata metadata) {
            processCalls++;
            capturedContext = context;
            capturedMetadata = metadata;

            if (replay != null) {
                return (T) replay;
            }

            try {
                return context.getOperationCallback().call();
            } catch (RuntimeException re) {
                throw re;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
    }

    static class RecordingOperationMetadataManager implements OperationMetadataManager {

        RawOperationMetadata capturedRawMetadata;
        String capturedConfigName;
        OperationMetadata produced;
        boolean useFingerprint = true;
        int mergeCalls;

        final RecordingFingerprintPolicy fingerprintPolicy = new RecordingFingerprintPolicy();

        @Override
        public OperationMetadata merge(RawOperationMetadata metadata) {
            return merge(metadata, null);
        }

        @Override
        public OperationMetadata merge(RawOperationMetadata metadata, String configName) {
            mergeCalls++;
            capturedRawMetadata = metadata;
            capturedConfigName = configName;
            produced = TestOperationMetadata.builder()
                    .headerName(metadata.getHeaderName() == null ? HEADER_NAME : metadata.getHeaderName())
                    .ttl(metadata.getTtl())
                    .useFingerprint(useFingerprint)
                    .fingerprintPolicy(fingerprintPolicy)
                    .build();
            return produced;
        }
    }

    static class RecordingFingerprintPolicy implements FingerprintPolicy {

        static final String FINGERPRINT = "fingerprint";

        int generateCalls;

        @Override
        public String generate(RequestContext context) {
            generateCalls++;
            return FINGERPRINT;
        }

        @Override
        public boolean compare(String previous, String current) {
            return previous.equals(current);
        }

        @Override
        public void handle(FingerprintMismatchContext context) { }
    }

    static class HeaderKeyExtractor implements KeyExtractor {

        @Override
        public UUID extract(String headerName, RequestContext context) {
            return UUID.fromString(context.getHeader(headerName));
        }

        @Override
        public RequestType getRequestType() {
            return RequestType.HTTP;
        }
    }

    record TestRequestContext(String headerName, String headerValue) implements RequestContext {

        @Override
        public RequestType getRequestType() {
            return RequestType.HTTP;
        }

        @Override
        public String getHeader(String name) {
            return headerName.equals(name) ? headerValue : null;
        }

        @Override
        public String getMethod() {
            return "POST";
        }

        @Override
        public String getPath() {
            return "/payments";
        }

        @Override
        public byte[] getBodyBytes() {
            return "{\"amount\":10}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
