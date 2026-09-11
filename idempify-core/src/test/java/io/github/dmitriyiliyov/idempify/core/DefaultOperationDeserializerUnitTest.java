package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The row comes back as strings, and the type to read the result into comes from the caller rather than from
 * the row. A column left {@code NULL} must not reach a deserializer at all, and neither must a result whose
 * type the caller did not ask for.
 */
@ExtendWith(MockitoExtension.class)
class DefaultOperationDeserializerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-10T12:00:00Z");
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);

    @Mock
    private ResultDeserializer resultDeserializer;
    @Mock
    private ResponseDeserializer responseDeserializer;

    @Test
    @DisplayName("UT constructor() when resultDeserializer is null should throw NullPointerException")
    void constructor_whenResultDeserializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultOperationDeserializer(null, responseDeserializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resultDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when responseDeserializer is null should throw NullPointerException")
    void constructor_whenResponseDeserializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultOperationDeserializer(resultDeserializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("responseDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT deserialize() should rebuild every column of the operation from the row")
    void deserialize_shouldRebuildEveryColumnOfOperationFromRow() {
        // given
        Response response = new DefaultResponse(201, new byte [] {1}, "application/json", Map.of());
        when(resultDeserializer.deserialize("raw-result", RESULT_TYPE)).thenReturn("result");
        when(responseDeserializer.deserialize("raw-response")).thenReturn(response);

        // when
        Operation result = tested().deserialize(row("raw-result", "raw-response"), RESULT_TYPE);

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.getResult()).isEqualTo("result");
        assertThat(result.getResultType()).isEqualTo(RESULT_TYPE);
        assertThat(result.getResponse()).isSameAs(response);
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
        assertThat(result.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UT deserialize() when the row is null should hand back null")
    void deserialize_whenRowIsNull_shouldHandBackNull() {
        // when
        Operation result = tested().deserialize(null, RESULT_TYPE);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(resultDeserializer, responseDeserializer);
    }

    @Test
    @DisplayName("UT deserialize() when result and response columns are null should not ask either deserializer")
    void deserialize_whenResultAndResponseColumnsAreNull_shouldNotAskEitherDeserializer() {
        // when
        Operation result = tested().deserialize(row(null, null), RESULT_TYPE);

        // then
        assertThat(result.getResult()).isNull();
        assertThat(result.getResponse()).isNull();
        verifyNoInteractions(resultDeserializer, responseDeserializer);
    }

    @Test
    @DisplayName("UT deserialize() when the caller wants no resultType should leave the result unread")
    void deserialize_whenCallerWantsNoResultType_shouldLeaveResultUnread() {
        // when
        Operation result = tested().deserialize(row("raw-result", null), null);

        // then
        assertThat(result.getResult()).isNull();
        assertThat(result.getResultType()).isNull();
        verifyNoInteractions(resultDeserializer);
    }

    @Test
    @DisplayName("UT deserialize() when the caller wants no resultType should still rebuild the response")
    void deserialize_whenCallerWantsNoResultType_shouldStillRebuildResponse() {
        // given
        Response response = new DefaultResponse(201, new byte [] {1}, "application/json", Map.of());
        when(responseDeserializer.deserialize("raw-response")).thenReturn(response);

        // when
        Operation result = tested().deserialize(row("raw-result", "raw-response"), null);

        // then
        assertThat(result.getResponse()).isSameAs(response);
        verifyNoInteractions(resultDeserializer);
    }

    private DefaultOperationDeserializer tested() {
        return new DefaultOperationDeserializer(resultDeserializer, responseDeserializer);
    }

    private static RawOperation row(String result, String response) {
        return new RawOperation(
                KEY,
                OperationStatus.PROCESSED,
                true,
                result,
                response,
                "fingerprint",
                EXPIRES_AT,
                NOW
        );
    }
}
