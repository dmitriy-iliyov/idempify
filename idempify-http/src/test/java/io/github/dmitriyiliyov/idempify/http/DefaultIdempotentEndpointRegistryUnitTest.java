package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.aop.Idempotent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultIdempotentEndpointRegistryUnitTest {

    @Mock
    RequestMappingHandlerMapping handlerMapping;

    @InjectMocks
    DefaultIdempotentEndpointRegistry tested;

    @Test
    @DisplayName("UT constructor when handlerMapping is null should throw NullPointerException")
    void constructor_whenHandlerMappingIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentEndpointRegistry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handlerMapping cannot be null");
    }

    @Test
    @DisplayName("UT getPatterns() before event should return empty set")
    void getPatterns_beforeEvent_shouldReturnEmptySet() {
        // when
        Set<String> result = tested.getPatterns();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("UT onApplicationEvent() when no methods annotated with Idempotent should not add any patterns")
    void onApplicationEvent_whenNoMethodsAnnotatedWithIdempotent_shouldNotAddAnyPatterns() {
        // given
        ContextRefreshedEvent event = mock(ContextRefreshedEvent.class);
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        RequestMappingInfo mappingInfo = mock(RequestMappingInfo.class);

        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(mappingInfo, handlerMethod));
        when(handlerMethod.hasMethodAnnotation(Idempotent.class)).thenReturn(false);

        // when
        tested.onApplicationEvent(event);

        // then
        assertThat(tested.getPatterns()).isEmpty();
        verify(mappingInfo, never()).getPatternValues();
    }

    @Test
    @DisplayName("UT onApplicationEvent() when single method annotated with Idempotent should add its pattern")
    void onApplicationEvent_whenSingleMethodAnnotatedWithIdempotent_shouldAddItsPattern() {
        // given
        ContextRefreshedEvent event = mock(ContextRefreshedEvent.class);
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        RequestMappingInfo mappingInfo = mock(RequestMappingInfo.class);

        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(mappingInfo, handlerMethod));
        when(handlerMethod.hasMethodAnnotation(Idempotent.class)).thenReturn(true);
        when(mappingInfo.getPatternValues()).thenReturn(Set.of("/api/payment"));

        // when
        tested.onApplicationEvent(event);

        // then
        assertThat(tested.getPatterns()).containsExactly("/api/payment");
    }

    @Test
    @DisplayName("UT onApplicationEvent() when method annotated with Idempotent has multiple patterns should add all patterns")
    void onApplicationEvent_whenMethodAnnotatedWithIdempotentHasMultiplePatterns_shouldAddAllPatterns() {
        // given
        ContextRefreshedEvent event = mock(ContextRefreshedEvent.class);
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        RequestMappingInfo mappingInfo = mock(RequestMappingInfo.class);

        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(mappingInfo, handlerMethod));
        when(handlerMethod.hasMethodAnnotation(Idempotent.class)).thenReturn(true);
        when(mappingInfo.getPatternValues()).thenReturn(Set.of("/api/payment", "/api/v2/payment"));

        // when
        tested.onApplicationEvent(event);

        // then
        assertThat(tested.getPatterns()).containsExactlyInAnyOrder("/api/payment", "/api/v2/payment");
    }

    @Test
    @DisplayName("UT onApplicationEvent() when multiple methods annotated with Idempotent should add all their patterns")
    void onApplicationEvent_whenMultipleMethodsAnnotatedWithIdempotent_shouldAddAllTheirPatterns() {
        // given
        ContextRefreshedEvent event = mock(ContextRefreshedEvent.class);
        HandlerMethod firstMethod = mock(HandlerMethod.class);
        HandlerMethod secondMethod = mock(HandlerMethod.class);
        RequestMappingInfo firstMappingInfo = mock(RequestMappingInfo.class);
        RequestMappingInfo secondMappingInfo = mock(RequestMappingInfo.class);

        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                firstMappingInfo, firstMethod,
                secondMappingInfo, secondMethod
        ));
        when(firstMethod.hasMethodAnnotation(Idempotent.class)).thenReturn(true);
        when(secondMethod.hasMethodAnnotation(Idempotent.class)).thenReturn(true);
        when(firstMappingInfo.getPatternValues()).thenReturn(Set.of("/api/payment"));
        when(secondMappingInfo.getPatternValues()).thenReturn(Set.of("/api/order"));

        // when
        tested.onApplicationEvent(event);

        // then
        assertThat(tested.getPatterns()).containsExactlyInAnyOrder("/api/payment", "/api/order");
    }

    @Test
    @DisplayName("UT onApplicationEvent() when only some methods annotated with Idempotent should add only annotated patterns")
    void onApplicationEvent_whenOnlySomeMethodsAnnotatedWithIdempotent_shouldAddOnlyAnnotatedPatterns() {
        // given
        ContextRefreshedEvent event = mock(ContextRefreshedEvent.class);
        HandlerMethod idempotentMethod = mock(HandlerMethod.class);
        HandlerMethod nonIdempotentMethod = mock(HandlerMethod.class);
        RequestMappingInfo idempotentMappingInfo = mock(RequestMappingInfo.class);
        RequestMappingInfo nonIdempotentMappingInfo = mock(RequestMappingInfo.class);

        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                idempotentMappingInfo, idempotentMethod,
                nonIdempotentMappingInfo, nonIdempotentMethod
        ));
        when(idempotentMethod.hasMethodAnnotation(Idempotent.class)).thenReturn(true);
        when(nonIdempotentMethod.hasMethodAnnotation(Idempotent.class)).thenReturn(false);
        when(idempotentMappingInfo.getPatternValues()).thenReturn(Set.of("/api/payment"));

        // when
        tested.onApplicationEvent(event);

        // then
        assertThat(tested.getPatterns()).containsExactly("/api/payment");
        verify(nonIdempotentMappingInfo, never()).getPatternValues();
    }
}