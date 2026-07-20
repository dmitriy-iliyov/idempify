package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DelegatingKeyExtractorUnitTest {

    @Test
    @DisplayName("UT constructor when extractors is null should throw NullPointerException")
    void constructor_whenExtractorsIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DelegatingKeyExtractor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("extractors cannot be null");
    }

    @Test
    @DisplayName("UT constructor when extractors contains multiple extractors should register all extractors")
    void constructor_whenExtractorsContainsMultipleExtractors_shouldRegisterAllExtractors() {
        // given
        KeyExtractor httpExtractor = mock(KeyExtractor.class);
        KeyExtractor grpcExtractor = mock(KeyExtractor.class);

        when(httpExtractor.getRequestType()).thenReturn(RequestType.HTTP);
        when(grpcExtractor.getRequestType()).thenReturn(RequestType.GRPC);

        DelegatingKeyExtractor tested = new DelegatingKeyExtractor(
                List.of(httpExtractor, grpcExtractor)
        );

        RequestContext httpContext = mock(RequestContext.class);
        when(httpContext.getRequestType()).thenReturn(RequestType.HTTP);

        RequestContext grpcContext = mock(RequestContext.class);
        when(grpcContext.getRequestType()).thenReturn(RequestType.GRPC);

        UUID httpId = UUID.randomUUID();
        UUID grpcId = UUID.randomUUID();

        when(httpExtractor.extract("header", httpContext))
                .thenReturn(httpId);

        when(grpcExtractor.extract("header", grpcContext))
                .thenReturn(grpcId);

        // when
        UUID httpResult = tested.extract("header", httpContext);
        UUID grpcResult = tested.extract("header", grpcContext);

        // then
        assertThat(httpResult).isEqualTo(httpId);
        assertThat(grpcResult).isEqualTo(grpcId);

        verify(httpExtractor, times(1)).extract("header", httpContext);
        verify(grpcExtractor, times(1)).extract("header", grpcContext);
    }

    @Test
    @DisplayName("UT extract() when requestType is null should throw NullPointerException")
    void extract_whenRequestTypeIsNull_shouldThrowNullPointerException() {
        // given
        KeyExtractor extractor = mock(KeyExtractor.class);
        when(extractor.getRequestType()).thenReturn(RequestType.HTTP);

        DelegatingKeyExtractor tested = new DelegatingKeyExtractor(List.of(extractor));

        RequestContext context = mock(RequestContext.class);
        when(context.getRequestType()).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> tested.extract("header-name", context))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestType cannot be null");
    }

    @Test
    @DisplayName("UT extract() when context is null should throw NullPointerException")
    void extract_whenContextIsNull_shouldThrowNullPointerException() {
        // given
        KeyExtractor extractor = mock(KeyExtractor.class);
        when(extractor.getRequestType()).thenReturn(RequestType.HTTP);

        DelegatingKeyExtractor tested = new DelegatingKeyExtractor(List.of(extractor));

        // when / then
        assertThatThrownBy(() -> tested.extract("header-name", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UT extract() when extractor not found should throw IllegalStateException")
    void extract_whenExtractorNotFound_shouldThrowIllegalStateException() {
        // given
        KeyExtractor extractor = mock(KeyExtractor.class);
        when(extractor.getRequestType()).thenReturn(RequestType.HTTP);

        DelegatingKeyExtractor tested = new DelegatingKeyExtractor(List.of(extractor));

        RequestContext context = mock(RequestContext.class);
        when(context.getRequestType()).thenReturn(RequestType.GRPC);

        // when / then
        assertThatThrownBy(() -> tested.extract("header-name", context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KeyExtractor for");
    }

    @Test
    @DisplayName("UT extract() when extractor found should call extract and return UUID")
    void extract_whenExtractorFound_shouldCallExtractAndReturnUUID() {
        // given
        KeyExtractor extractor = mock(KeyExtractor.class);

        when(extractor.getRequestType()).thenReturn(RequestType.HTTP);

        DelegatingKeyExtractor tested = new DelegatingKeyExtractor(List.of(extractor));

        String headerName = "X-Idempotency-Key";
        RequestContext context = mock(RequestContext.class);

        when(context.getRequestType()).thenReturn(RequestType.HTTP);

        UUID expectedUuid = UUID.randomUUID();

        when(extractor.extract(headerName, context))
                .thenReturn(expectedUuid);

        // when
        UUID result = tested.extract(headerName, context);

        // then
        assertThat(result).isEqualTo(expectedUuid);

        verify(extractor, times(1))
                .extract(headerName, context);

        verifyNoMoreInteractions(extractor);
    }

    @Test
    @DisplayName("UT extract() when extractor returns null should return null")
    void extract_whenExtractorReturnsNull_shouldReturnNull() {
        // given
        KeyExtractor extractor = mock(KeyExtractor.class);

        when(extractor.getRequestType()).thenReturn(RequestType.HTTP);

        DelegatingKeyExtractor tested = new DelegatingKeyExtractor(List.of(extractor));

        RequestContext context = mock(RequestContext.class);

        when(context.getRequestType()).thenReturn(RequestType.HTTP);

        when(extractor.extract("header-name", context))
                .thenReturn(null);

        // when
        UUID result = tested.extract("header-name", context);

        // then
        assertThat(result).isNull();

        verify(extractor, times(1))
                .extract("header-name", context);
    }

    @Test
    @DisplayName("UT getRequestType() should throw IllegalStateException")
    void getRequestType_shouldThrowIllegalStateException() {
        // given
        KeyExtractor extractor = mock(KeyExtractor.class);

        when(extractor.getRequestType())
                .thenReturn(RequestType.HTTP);

        DelegatingKeyExtractor tested = new DelegatingKeyExtractor(List.of(extractor));

        // when / then
        assertThatThrownBy(tested::getRequestType)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Should not be called");
    }
}