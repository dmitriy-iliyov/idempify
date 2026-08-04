package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultOperationMetadataFactoryUnitTest {

    @Mock
    OperationMetadataCache cache;

    @Mock
    OperationMetadataManager manager;

    @Mock
    ProceedingJoinPoint jp;

    @Mock
    MethodSignature signature;

    @InjectMocks
    DefaultOperationMetadataFactory tested;

    @Test
    @DisplayName("UT constructor when cache is null should throw NullPointerException")
    void constructor_whenCacheIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationMetadataFactory(null, manager))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cache cannot be null");
    }

    @Test
    @DisplayName("UT constructor when manager is null should throw NullPointerException")
    void constructor_whenManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationMetadataFactory(cache, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("manager cannot be null");
    }

    @Test
    @DisplayName("UT generate() when the method is already cached should return the cached metadata")
    void generate_whenMethodIsAlreadyCached_shouldReturnCachedMetadata() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata cached = TestOperationMetadata.builder().build();

        stubJoinPoint(method);
        when(cache.get(method)).thenReturn(cached);

        // when
        OperationMetadata result = tested.generate(annotation("action"), jp);

        // then
        assertThat(result).isSameAs(cached);
        verifyNoInteractions(manager);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("UT generate() when the method is not cached should resolve the metadata and cache it")
    void generate_whenMethodIsNotCached_shouldResolveMetadataAndCacheIt() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata resolved = TestOperationMetadata.builder().build();

        stubJoinPoint(method);
        when(cache.get(method)).thenReturn(null);
        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(resolved);

        // when
        OperationMetadata result = tested.generate(annotation("action"), jp);

        // then
        assertThat(result).isSameAs(resolved);
        verify(cache, times(1)).put(method, resolved);
    }

    @Test
    @DisplayName("UT generate() when the annotation names no config should merge without a config name")
    void generate_whenAnnotationNamesNoConfig_shouldMergeWithoutConfigName() throws NoSuchMethodException {
        // given
        stubJoinPoint(method("action"));
        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(TestOperationMetadata.builder().build());

        // when
        tested.generate(annotation("action"), jp);

        // then
        verify(manager, times(1)).merge(any(RawOperationMetadata.class));
        verify(manager, never()).merge(any(), anyString());
    }

    @Test
    @DisplayName("UT generate() when the annotation names a config should merge with that config name")
    void generate_whenAnnotationNamesConfig_shouldMergeWithThatConfigName() throws NoSuchMethodException {
        // given
        stubJoinPoint(method("actionWithConfig"));
        when(manager.merge(any(RawOperationMetadata.class), eq("payments")))
                .thenReturn(TestOperationMetadata.builder().build());

        // when
        tested.generate(annotation("actionWithConfig"), jp);

        // then
        verify(manager, times(1)).merge(any(RawOperationMetadata.class), eq("payments"));
        verify(manager, never()).merge(any());
    }

    @Test
    @DisplayName("UT generate() when the annotation specifies every attribute should carry them all into the raw metadata")
    void generate_whenAnnotationSpecifiesEveryAttribute_shouldCarryThemAllIntoRawMetadata() throws NoSuchMethodException {
        // given
        RawOperationMetadata expected = DefaultRawOperationMetadata.builder()
                .headerName("X-Payment-Key")
                .ttl(48)
                .timeUnit(TimeUnit.HOURS)
                .conflictHandleStrategy(ConflictHandleStrategy.WAIT)
                .fingerprintToggle(Toggle.ENABLE)
                .cacheToggle(Toggle.ENABLE)
                .cache4xxToggle(Toggle.DISABLE)
                .cache5xxToggle(Toggle.DISABLE)
                .build();
        ArgumentCaptor<RawOperationMetadata> captor = ArgumentCaptor.forClass(RawOperationMetadata.class);

        stubJoinPoint(method("fullyConfiguredAction"));
        when(manager.merge(any(RawOperationMetadata.class), eq("payments")))
                .thenReturn(TestOperationMetadata.builder().build());

        // when
        tested.generate(annotation("fullyConfiguredAction"), jp);

        // then
        verify(manager, times(1)).merge(captor.capture(), eq("payments"));
        assertThat(captor.getValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("UT generate() when the annotation specifies a ttl should convert it with its time unit")
    void generate_whenAnnotationSpecifiesTtl_shouldConvertItWithItsTimeUnit() throws NoSuchMethodException {
        // given
        ArgumentCaptor<RawOperationMetadata> captor = ArgumentCaptor.forClass(RawOperationMetadata.class);

        stubJoinPoint(method("actionWithMinutesTtl"));
        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(TestOperationMetadata.builder().build());

        // when
        tested.generate(annotation("actionWithMinutesTtl"), jp);

        // then
        verify(manager, times(1)).merge(captor.capture());
        assertThat(captor.getValue().getTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("UT generate() when the annotation leaves everything at its default should leave the raw metadata unspecified")
    void generate_whenAnnotationLeavesEverythingAtDefault_shouldLeaveRawMetadataUnspecified() throws NoSuchMethodException {
        // given
        ArgumentCaptor<RawOperationMetadata> captor = ArgumentCaptor.forClass(RawOperationMetadata.class);

        stubJoinPoint(method("action"));
        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(TestOperationMetadata.builder().build());

        // when
        tested.generate(annotation("action"), jp);

        // then
        verify(manager, times(1)).merge(captor.capture());
        RawOperationMetadata raw = captor.getValue();
        assertThat(raw.getHeaderName()).isNull();
        assertThat(raw.getTtl()).isNull();
        assertThat(raw.getConflictHandleStrategy()).isEqualTo(ConflictHandleStrategy.UNSELECT);
        assertThat(raw.getFingerprintToggle()).isEqualTo(Toggle.DEFAULT);
        assertThat(raw.getCacheToggle()).isEqualTo(Toggle.DEFAULT);
        assertThat(raw.getCache4xxToggle()).isEqualTo(Toggle.DEFAULT);
        assertThat(raw.getCache5xxToggle()).isEqualTo(Toggle.DEFAULT);
    }

    @Test
    @DisplayName("UT generate() when the join point has no target should still resolve the metadata")
    void generate_whenJoinPointHasNoTarget_shouldStillResolveMetadata() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata resolved = TestOperationMetadata.builder().build();

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(null);
        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(resolved);

        // when
        OperationMetadata result = tested.generate(annotation("action"), jp);

        // then
        assertThat(result).isSameAs(resolved);
        verify(cache, times(1)).put(method, resolved);
    }

    @Test
    @DisplayName("UT generate() when another caller resolved the metadata first should take theirs without resolving again")
    void generate_whenAnotherCallerResolvedMetadataFirst_shouldTakeTheirsWithoutResolvingAgain() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata theirs = TestOperationMetadata.builder().build();

        stubJoinPoint(method);
        when(cache.get(method)).thenReturn(null, theirs);

        // when
        OperationMetadata result = tested.generate(annotation("action"), jp);

        // then
        assertThat(result).isSameAs(theirs);
        verifyNoInteractions(manager);
        verify(cache, never()).put(any(), any());
    }

    private void stubJoinPoint(Method method) {
        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
    }

    private Method method(String name) throws NoSuchMethodException {
        return TestTarget.class.getMethod(name);
    }

    private Idempotent annotation(String methodName) throws NoSuchMethodException {
        return method(methodName).getAnnotation(Idempotent.class);
    }

    private static class TestTarget {

        @Idempotent
        public String action() {
            return "action";
        }

        @Idempotent(config = "payments")
        public String actionWithConfig() {
            return "actionWithConfig";
        }

        @Idempotent(ttl = 30, timeUnit = TimeUnit.MINUTES)
        public String actionWithMinutesTtl() {
            return "actionWithMinutesTtl";
        }

        @Idempotent(
                config = "payments",
                headerName = "X-Payment-Key",
                ttl = 48,
                timeUnit = TimeUnit.HOURS,
                onConflict = ConflictHandleStrategy.WAIT,
                useFingerprint = Toggle.ENABLE,
                shouldCache = Toggle.ENABLE,
                shouldCache4xx = Toggle.DISABLE,
                shouldCache5xx = Toggle.DISABLE
        )
        public String fullyConfiguredAction() {
            return "fullyConfiguredAction";
        }
    }
}
