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
 * The non-blocking row is the blocking one plus the claim: what is judged beside the columns both share is that
 * the lock token and the lease reach the store as they are.
 */
@ExtendWith(MockitoExtension.class)
class NonBlockingOperationSerializerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOCK_TOKEN = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Long REMAINING_LOCK_MILLIS = 300_000L;
    private static final long REMAINING_MILLIS = 86_400_000L;

    @Mock
    private ResultSerializer resultSerializer;
    @Mock
    private ResponseSerializer responseSerializer;

    @Test
    @DisplayName("UT constructor() when resultSerializer is null should throw NullPointerException")
    void constructor_whenResultSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new NonBlockingOperationSerializer(null, responseSerializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resultSerializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when responseSerializer is null should throw NullPointerException")
    void constructor_whenResponseSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new NonBlockingOperationSerializer(resultSerializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("responseSerializer cannot be null");
    }

    @Test
    @DisplayName("UT serialize() should carry every column of the operation and its claim into the row")
    void serialize_shouldCarryEveryColumnOfOperationAndItsClaimIntoRow() {
        // given
        Response response = new DefaultResponse(201, new byte [] {1}, "application/json", Map.of());
        when(resultSerializer.serialize("result")).thenReturn("raw-result");
        when(responseSerializer.serialize(response)).thenReturn("raw-response");

        // when
        RawNonBlockingOperation result = tested().serialize(operation("result", response));

        // then
        assertThat(result.idempotencyKey()).isEqualTo(KEY);
        assertThat(result.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.result()).isEqualTo("raw-result");
        assertThat(result.response()).isEqualTo("raw-response");
        assertThat(result.fingerprint()).isEqualTo("fingerprint");
        assertThat(result.remainingMillis()).isEqualTo(REMAINING_MILLIS);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(result.remainingLockMillis()).isEqualTo(REMAINING_LOCK_MILLIS);
    }

    @Test
    @DisplayName("UT serialize() when the operation is null should hand back null without asking either serializer")
    void serialize_whenOperationIsNull_shouldHandBackNullWithoutAskingEitherSerializer() {
        // when
        RawNonBlockingOperation result = tested().serialize(null);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(resultSerializer, responseSerializer);
    }

    @Test
    @DisplayName("UT serialize() when a claim carries neither result nor response should leave both columns empty")
    void serialize_whenClaimCarriesNeitherResultNorResponse_shouldLeaveBothColumnsEmpty() {
        // when
        RawNonBlockingOperation result = tested().serialize(operation(null, null));

        // then
        assertThat(result.result()).isNull();
        assertThat(result.response()).isNull();
        verifyNoInteractions(resultSerializer, responseSerializer);
    }

    private NonBlockingOperationSerializer tested() {
        return new NonBlockingOperationSerializer(resultSerializer, responseSerializer);
    }

    private static NonBlockingOperation operation(Object result, Response response) {
        return new NonBlockingOperation(
                KEY,
                OperationStatus.PROCESSED,
                true,
                result,
                ResultType.ofClass(String.class),
                response,
                "fingerprint",
                REMAINING_MILLIS,
                NOW,
                LOCK_TOKEN,
                REMAINING_LOCK_MILLIS
        );
    }
}
