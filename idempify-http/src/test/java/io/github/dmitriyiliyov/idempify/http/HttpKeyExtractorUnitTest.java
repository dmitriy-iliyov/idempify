package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpKeyExtractorUnitTest {

    @InjectMocks
    HttpKeyExtractor tested;

    @Test
    @DisplayName("UT extract() when header is null should throw EmptyIdempotencyKeyException")
    void extract_whenHeaderIsNull_shouldThrowEmptyIdempotencyKeyException() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn(null);

        // when + then
        assertThatThrownBy(() -> tested.extract(headerName, context))
                .isInstanceOf(EmptyIdempotencyKeyException.class)
                .hasMessageContaining(headerName);
    }

    @Test
    @DisplayName("UT extract() when header is blank should throw EmptyIdempotencyKeyException")
    void extract_whenHeaderIsBlank_shouldThrowEmptyIdempotencyKeyException() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn("   ");

        // when + then
        assertThatThrownBy(() -> tested.extract(headerName, context))
                .isInstanceOf(EmptyIdempotencyKeyException.class)
                .hasMessageContaining(headerName);
    }

    @Test
    @DisplayName("UT extract() when header is valid UUID should return UUID")
    void extract_whenHeaderIsValidUuid_shouldReturnUuid() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        UUID expected = UUID.randomUUID();
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn(expected.toString());

        // when
        UUID result = tested.extract(headerName, context);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("UT extract() when header is invalid UUID should throw InvalidIdempotencyKeyException")
    void extract_whenHeaderIsInvalidUuid_shouldThrowInvalidIdempotencyKeyException() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn("not-a-uuid");

        // when / then
        assertThatThrownBy(() -> tested.extract(headerName, context))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    @DisplayName("UT getRequestType() should return HTTP")
    void getRequestType_shouldReturnHttp() {
        // when
        RequestType result = tested.getRequestType();

        // then
        assertThat(result).isEqualTo(RequestType.HTTP);
    }
}