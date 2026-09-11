package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
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
 * Every column of the row is written here, so what is judged is that nothing is dropped on the way out and
 * that each half of the operation reaches the serializer that owns it. The declared type of the result is
 * not among the columns: it belongs to the call site, and the store never sees it.
 */
@ExtendWith(MockitoExtension.class)
class DefaultOperationSerializerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-10T12:00:00Z");

    @Mock
    private ResultSerializer resultSerializer;
    @Mock
    private ResponseSerializer responseSerializer;

    @Test
    @DisplayName("UT constructor() when resultSerializer is null should throw NullPointerException")
    void constructor_whenResultSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultOperationSerializer(null, responseSerializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resultSerializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when responseSerializer is null should throw NullPointerException")
    void constructor_whenResponseSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultOperationSerializer(resultSerializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("responseSerializer cannot be null");
    }

    @Test
    @DisplayName("UT serialize() should carry every column of the operation into the row")
    void serialize_shouldCarryEveryColumnOfOperationIntoRow() {
        // given
        Response response = new DefaultResponse(201, new byte [] {1}, "application/json", Map.of());
        when(resultSerializer.serialize("result")).thenReturn("raw-result");
        when(responseSerializer.serialize(response)).thenReturn("raw-response");
        DefaultOperationSerializer tested = tested();

        // when
        RawOperation result = tested.serialize(operation("result", response));

        // then
        assertThat(result.idempotencyKey()).isEqualTo(KEY);
        assertThat(result.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.result()).isEqualTo("raw-result");
        assertThat(result.response()).isEqualTo("raw-response");
        assertThat(result.fingerprint()).isEqualTo("fingerprint");
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UT serialize() when the operation is null should hand back null without asking either serializer")
    void serialize_whenOperationIsNull_shouldHandBackNullWithoutAskingEitherSerializer() {
        // when
        RawOperation result = tested().serialize(null);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(resultSerializer, responseSerializer);
    }

    @Test
    @DisplayName("UT serialize() when a claim carries neither result nor response should leave both columns empty")
    void serialize_whenClaimCarriesNeitherResultNorResponse_shouldLeaveBothColumnsEmpty() {
        // when
        RawOperation result = tested().serialize(operation(null, null));

        // then
        assertThat(result.result()).isNull();
        assertThat(result.response()).isNull();
        verifyNoInteractions(resultSerializer, responseSerializer);
    }

    private DefaultOperationSerializer tested() {
        return new DefaultOperationSerializer(resultSerializer, responseSerializer);
    }

    private static Operation operation(Object result, Response response) {
        return new Operation(
                KEY,
                OperationStatus.PROCESSED,
                true,
                result,
                ResultType.ofClass(String.class),
                response,
                "fingerprint",
                EXPIRES_AT,
                NOW
        );
    }
}
