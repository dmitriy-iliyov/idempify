package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultOperationMetadataResolverUnitTest {

    @Mock
    OperationMetadataCache cache;

    @Mock
    OperationMetadataManager manager;

    @InjectMocks
    DefaultOperationMetadataResolver tested;

    @Test
    @DisplayName("UT constructor when cache is null should throw NullPointerException")
    void constructor_whenCacheIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationMetadataResolver(null, manager))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cache cannot be null");
    }

    @Test
    @DisplayName("UT constructor when manager is null should throw NullPointerException")
    void constructor_whenManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationMetadataResolver(cache, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("manager cannot be null");
    }

    @Test
    @DisplayName("UT resolve() when method is null should throw NullPointerException")
    void resolve_Metadata_whenMethodIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> tested.resolve(null, TestTarget.class))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("method cannot be null");
    }

    @Test
    @DisplayName("UT resolve() when the method is already cached should return the cached metadata")
    void resolve_Metadata_whenMethodIsAlreadyCached_shouldReturnCachedMetadata() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata cached = mock(OperationMetadata.class);

        when(cache.get(method)).thenReturn(cached);

        // when
        OperationMetadata result = tested.resolve(method, TestTarget.class);

        // then
        assertThat(result).isSameAs(cached);
        verifyNoInteractions(manager);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("UT resolve() when the method is not cached should resolve the metadata and cache it")
    void resolve_whenMethodIsNotCached_shouldResolveMetadataMetadataAndCacheIt() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata resolved = mock(OperationMetadata.class);

        when(cache.get(method)).thenReturn(null);
        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(resolved);

        // when
        OperationMetadata result = tested.resolve(method, TestTarget.class);

        // then
        assertThat(result).isSameAs(resolved);
        verify(cache, times(1)).put(method, resolved);
    }

    @Test
    @DisplayName("UT resolve() when the method carries no Idempotent should throw IllegalStateException")
    void resolve_Metadata_whenMethodCarriesNoIdempotent_shouldThrowIllegalStateException() throws NoSuchMethodException {
        // given
        Method method = TestTarget.class.getMethod("notAnnotatedAction");

        // when // then
        assertThatThrownBy(() -> tested.resolve(method, TestTarget.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not annotated with @Idempotent");
        verifyNoInteractions(manager);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("UT resolve() when the method is declared on an interface should key on the implementation method")
    void resolve_Metadata_whenMethodIsDeclaredOnInterface_shouldKeyOnImplementationMethod() throws NoSuchMethodException {
        // given
        Method interfaceMethod = TestContract.class.getMethod("contractAction");
        Method implementationMethod = TestTarget.class.getMethod("contractAction");
        OperationMetadata resolved = mock(OperationMetadata.class);

        when(cache.get(implementationMethod)).thenReturn(null);
        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(resolved);

        // when
        OperationMetadata result = tested.resolve(interfaceMethod, TestTarget.class);

        // then
        assertThat(result).isSameAs(resolved);
        verify(cache, times(1)).put(implementationMethod, resolved);
    }

    @Test
    @DisplayName("UT resolve() when the annotation names no config should merge without a config name")
    void resolve_Metadata_whenAnnotationNamesNoConfig_shouldMergeWithoutConfigName() throws NoSuchMethodException {
        // given
        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(mock(OperationMetadata.class));

        // when
        tested.resolve(method("action"), TestTarget.class);

        // then
        verify(manager, times(1)).merge(any(RawOperationMetadata.class));
        verify(manager, never()).merge(any(), anyString());
    }

    @Test
    @DisplayName("UT resolve() when the annotation names a config should merge with that config name")
    void resolve_Metadata_whenAnnotationNamesConfig_shouldMergeWithThatConfigName() throws NoSuchMethodException {
        // given
        when(manager.merge(any(RawOperationMetadata.class), eq("payments")))
                .thenReturn(mock(OperationMetadata.class));

        // when
        tested.resolve(method("actionWithConfig"), TestTarget.class);

        // then
        verify(manager, times(1)).merge(any(RawOperationMetadata.class), eq("payments"));
        verify(manager, never()).merge(any());
    }

    @Test
    @DisplayName("UT resolve() when the annotation specifies every attribute should carry them all into the raw metadata")
    void resolve_Metadata_whenAnnotationSpecifiesEveryAttribute_shouldCarryThemAllIntoRawMetadata() throws NoSuchMethodException {
        // given
        RawOperationMetadata expected = DefaultRawOperationMetadata.builder()
                .headerName("X-Payment-Key")
                .ttl(48)
                .timeUnit(TimeUnit.HOURS)
                .conflictHandleStrategy(ConflictHandleStrategyToggle.WAIT)
                .fingerprintToggle(Toggle.ENABLE)
                .cache4xxToggle(Toggle.DISABLE)
                .cache5xxToggle(Toggle.DISABLE)
                .build();
        ArgumentCaptor<RawOperationMetadata> captor = ArgumentCaptor.forClass(RawOperationMetadata.class);

        when(manager.merge(any(RawOperationMetadata.class), eq("payments")))
                .thenReturn(mock(OperationMetadata.class));

        // when
        tested.resolve(method("fullyConfiguredAction"), TestTarget.class);

        // then
        verify(manager, times(1)).merge(captor.capture(), eq("payments"));
        assertThat(captor.getValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("UT resolve() when the annotation names an expression should say the key is not read from a header")
    void resolve_Metadata_whenAnnotationNamesExpression_shouldSayKeyIsNotReadFromHeader() throws NoSuchMethodException {
        // given
        ArgumentCaptor<RawOperationMetadata> captor = ArgumentCaptor.forClass(RawOperationMetadata.class);

        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(mock(OperationMetadata.class));

        // when
        tested.resolve(method("actionWithKeyExpression"), TestTarget.class);

        // then
        verify(manager, times(1)).merge(captor.capture());
        RawOperationMetadata raw = captor.getValue();
        assertThat(raw.useHeaderName()).isFalse();
        assertThat(raw.getHeaderName()).isNull();
    }

    @Test
    @DisplayName("UT resolve() when the annotation names both key sources should refuse instead of picking one")
    void resolve_Metadata_whenAnnotationNamesBothKeySources_shouldRefuseInsteadOfPickingOne() throws NoSuchMethodException {
        // given
        Method method = method("actionWithBothKeySources");

        // when / then
        assertThatThrownBy(() -> tested.resolve(method, TestTarget.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be combined");
        verifyNoInteractions(manager);
    }

    @Test
    @DisplayName("UT resolve() when the annotation names no expression should keep reading the key from a header")
    void resolve_Metadata_whenAnnotationNamesNoExpression_shouldKeepReadingKeyFromHeader() throws NoSuchMethodException {
        // given
        ArgumentCaptor<RawOperationMetadata> captor = ArgumentCaptor.forClass(RawOperationMetadata.class);

        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(mock(OperationMetadata.class));

        // when
        tested.resolve(method("action"), TestTarget.class);

        // then
        verify(manager, times(1)).merge(captor.capture());
        assertThat(captor.getValue().useHeaderName()).isTrue();
    }

    @Test
    @DisplayName("UT resolve() when the annotation specifies a ttl should convert it with its time unit")
    void resolve_Metadata_whenAnnotationSpecifiesTtl_shouldConvertItWithItsTimeUnit() throws NoSuchMethodException {
        // given
        ArgumentCaptor<RawOperationMetadata> captor = ArgumentCaptor.forClass(RawOperationMetadata.class);

        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(mock(OperationMetadata.class));

        // when
        tested.resolve(method("actionWithMinutesTtl"), TestTarget.class);

        // then
        verify(manager, times(1)).merge(captor.capture());
        assertThat(captor.getValue().getTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("UT resolve() when the annotation leaves everything at its default should leave the raw metadata unspecified")
    void resolve_Metadata_whenAnnotationLeavesEverythingAtDefault_shouldLeaveRawMetadataUnspecified() throws NoSuchMethodException {
        // given
        ArgumentCaptor<RawOperationMetadata> captor = ArgumentCaptor.forClass(RawOperationMetadata.class);

        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(mock(OperationMetadata.class));

        // when
        tested.resolve(method("action"), TestTarget.class);

        // then
        verify(manager, times(1)).merge(captor.capture());
        RawOperationMetadata raw = captor.getValue();
        assertThat(raw.getHeaderName()).isNull();
        assertThat(raw.getTtl()).isNull();
        assertThat(raw.getConflictHandleStrategy()).isEqualTo(ConflictHandleStrategyToggle.UNSELECTED);
        assertThat(raw.getFingerprintToggle()).isEqualTo(Toggle.UNSELECTED);
        assertThat(raw.getCache4xxToggle()).isEqualTo(Toggle.UNSELECTED);
        assertThat(raw.getCache5xxToggle()).isEqualTo(Toggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT resolve() when there is no target class should still resolve the metadata")
    void resolve_whenThereIsNoTargetClass_shouldStillResolveMetadataMetadata() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata resolved = mock(OperationMetadata.class);

        when(manager.merge(any(RawOperationMetadata.class))).thenReturn(resolved);

        // when
        OperationMetadata result = tested.resolve(method, null);

        // then
        assertThat(result).isSameAs(resolved);
        verify(cache, times(1)).put(method, resolved);
    }

    @Test
    @DisplayName("UT resolve() when another caller resolved the metadata first should take theirs without resolving again")
    void resolve_Metadata_whenAnotherCallerResolvedMetadataFirst_shouldTakeTheirsWithoutResolvingAgain() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata theirs = mock(OperationMetadata.class);

        when(cache.get(method)).thenReturn(null, theirs);

        // when
        OperationMetadata result = tested.resolve(method, TestTarget.class);

        // then
        assertThat(result).isSameAs(theirs);
        verifyNoInteractions(manager);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("UT resolve() when the target class is a proxy should take the metadata the class it stands for cached")
    void resolve_whenTargetClassIsProxy_shouldTakeMetadataClassItStandsForCached() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata cached = mock(OperationMetadata.class);

        when(cache.get(method)).thenReturn(cached);

        // when
        OperationMetadata result = tested.resolve(method, TestTarget$$SpringCGLIB$$0.class);

        // then
        assertThat(result).isSameAs(cached);
        verifyNoInteractions(manager);
        verify(cache, never()).put(any(), any());
    }

    private Method method(String name) throws NoSuchMethodException {
        return TestTarget.class.getMethod(name);
    }

    private interface TestContract {

        String contractAction();
    }

    private static class TestTarget$$SpringCGLIB$$0 extends TestTarget {
    }

    private static class TestTarget implements TestContract {

        @Idempotent
        public String action() {
            return "action";
        }

        public String notAnnotatedAction() {
            return "notAnnotatedAction";
        }

        @Override
        @Idempotent
        public String contractAction() {
            return "contractAction";
        }

        @Idempotent(config = "payments")
        public String actionWithConfig() {
            return "actionWithConfig";
        }

        @Idempotent(ttl = 30, timeUnit = TimeUnit.MINUTES)
        public String actionWithMinutesTtl() {
            return "actionWithMinutesTtl";
        }

        @Idempotent(idempotencyKey = "#dto.id")
        public String actionWithKeyExpression() {
            return "actionWithKeyExpression";
        }

        @Idempotent(idempotencyKey = "#dto.id", headerName = "X-Payment-Key")
        public String actionWithBothKeySources() {
            return "actionWithBothKeySources";
        }

        @Idempotent(
                config = "payments",
                headerName = "X-Payment-Key",
                ttl = 48,
                timeUnit = TimeUnit.HOURS,
                onConflict = ConflictHandleStrategyToggle.WAIT,
                useFingerprint = Toggle.ENABLE,
                cache4xx = Toggle.DISABLE,
                cache5xx = Toggle.DISABLE
        )
        public String fullyConfiguredAction() {
            return "fullyConfiguredAction";
        }
    }
}
