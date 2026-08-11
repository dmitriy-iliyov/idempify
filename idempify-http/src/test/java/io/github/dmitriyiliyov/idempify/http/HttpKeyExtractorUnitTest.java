package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.IdempotencyConstants;
import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
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
    @DisplayName("UT extract() when header is null should throw IdempotencyKeyException")
    void extract_whenHeaderIsNull_shouldThrowIdempotencyKeyException() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn(null);

        // when + then
        assertThatThrownBy(() -> tested.extract(headerName, context))
                .isInstanceOf(IdempotencyKeyException.class)
                .hasMessageContaining(headerName);
    }

    @Test
    @DisplayName("UT extract() when header is blank should throw IdempotencyKeyException")
    void extract_whenHeaderIsBlank_shouldThrowIdempotencyKeyException() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn("   ");

        // when + then
        assertThatThrownBy(() -> tested.extract(headerName, context))
                .isInstanceOf(IdempotencyKeyException.class)
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
                .isInstanceOf(IdempotencyKeyException.class);
    }

    @Test
    @DisplayName("UT extract() when header is an uppercase UUID should return the same key as its lowercase form")
    void extract_whenHeaderIsUppercaseUuid_shouldReturnSameKeyAsLowercaseForm() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        UUID expected = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn(expected.toString().toUpperCase());

        // when
        UUID result = tested.extract(headerName, context);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("UT extract() when header is an invalid UUID should name the rejected value")
    void extract_whenHeaderIsInvalidUuid_shouldNameRejectedValue() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn("not-a-uuid");

        // when / then
        assertThatThrownBy(() -> tested.extract(headerName, context))
                .isInstanceOf(IdempotencyKeyException.class)
                .hasMessageContaining("not-a-uuid")
                .hasMessageContaining(headerName);
    }

    @Test
    @DisplayName("UT extract() when the header value is padded with spaces should be rejected")
    void extract_whenHeaderValueIsPaddedWithSpaces_shouldBeRejected() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn(" aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee ");

        // when / then
        assertThatThrownBy(() -> tested.extract(headerName, context))
                .isInstanceOf(IdempotencyKeyException.class);
    }

    @Test
    @DisplayName("UT extract() when the header value is a non-canonical UUID should be rejected")
    void extract_whenHeaderValueIsNonCanonicalUuid_shouldBeRejected() {
        // given
        String headerName = IdempotencyConstants.HEADER_NAME;
        RequestContext context = mock(RequestContext.class);

        when(context.getHeader(headerName)).thenReturn("1-2-3-4-5");

        // when / then
        assertThatThrownBy(() -> tested.extract(headerName, context))
                .isInstanceOf(IdempotencyKeyException.class);
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