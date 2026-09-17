package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * The manager is what stands between a transport dealing in responses and a store dealing in text. Several of
 * its lookups come back empty for different reasons - no record at all, a record whose operation has not
 * answered yet, a response past its expiry - and the empty answer is all the caller gets of any.
 */
@ExtendWith(MockitoExtension.class)
class DefaultResponseManagerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-10T11:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofHours(1));

    @Mock
    private ResponseRepository repository;
    @Mock
    private ResponseSerializer serializer;
    @Mock
    private ResponseDeserializer deserializer;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("UT constructor() when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultResponseManager(null, serializer, deserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when serializer is null should throw NullPointerException")
    void constructor_whenSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultResponseManager(repository, null, deserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("serializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when deserializer is null should throw NullPointerException")
    void constructor_whenDeserializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultResponseManager(repository, serializer, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("deserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultResponseManager(repository, serializer, deserializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock cannot be null");
    }

    @Test
    @DisplayName("UT save() should hand the serialized response to the repository under the key")
    void save_shouldHandSerializedResponseToRepositoryUnderKey() {
        // given
        Response response = response();
        when(serializer.serialize(response)).thenReturn("raw-response");

        // when
        tested().save(KEY, response);

        // then
        verify(repository).save(KEY, "raw-response");
    }

    @Test
    @DisplayName("UT save() when the key is null should write nothing")
    void save_whenKeyIsNull_shouldWriteNothing() {
        // when
        tested().save(null, response());

        // then
        verifyNoInteractions(repository, serializer);
    }

    @Test
    @DisplayName("UT save() when there is no response should write nothing")
    void save_whenThereIsNoResponse_shouldWriteNothing() {
        // when
        tested().save(KEY, null);

        // then
        verifyNoInteractions(repository, serializer);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the record carries a response should hand it back with its fingerprint")
    void findByIdempotencyKey_whenRecordCarriesResponse_shouldHandItBackWithItsFingerprint() {
        // given
        Response response = response();
        when(repository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(container("raw-response")));
        when(deserializer.deserialize("raw-response")).thenReturn(response);

        // when
        Optional<ResponseContainer> result = tested().findByIdempotencyKey(KEY);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getResponse()).isSameAs(response);
        assertThat(result.get().getFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when there is no record should answer empty without reading anything")
    void findByIdempotencyKey_whenThereIsNoRecord_shouldAnswerEmptyWithoutReadingAnything() {
        // given
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        // when
        Optional<ResponseContainer> result = tested().findByIdempotencyKey(KEY);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(deserializer);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the operation has not answered yet should answer empty without reading anything")
    void findByIdempotencyKey_whenOperationHasNotAnsweredYet_shouldAnswerEmptyWithoutReadingAnything() {
        // given
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(container(null)));

        // when
        Optional<ResponseContainer> result = tested().findByIdempotencyKey(KEY);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(deserializer);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the response is past its expiry should answer empty without reading it")
    void findByIdempotencyKey_whenResponseIsPastItsExpiry_shouldAnswerEmptyWithoutReadingIt() {
        // given
        when(repository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(container("raw-response", NOW.minusMillis(1))));

        // when
        Optional<ResponseContainer> result = tested().findByIdempotencyKey(KEY);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(deserializer);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the response expires this very instant should still hand it back")
    void findByIdempotencyKey_whenResponseExpiresThisVeryInstant_shouldStillHandItBack() {
        // given
        Response response = response();
        when(repository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(container("raw-response", NOW)));
        when(deserializer.deserialize("raw-response")).thenReturn(response);

        // when
        Optional<ResponseContainer> result = tested().findByIdempotencyKey(KEY);

        // then
        assertThat(result).map(ResponseContainer::getResponse).containsSame(response);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the response carries no expiry should answer empty without reading it")
    void findByIdempotencyKey_whenResponseCarriesNoExpiry_shouldAnswerEmptyWithoutReadingIt() {
        // given
        when(repository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(container("raw-response", null)));

        // when
        Optional<ResponseContainer> result = tested().findByIdempotencyKey(KEY);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(deserializer);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the operation has not answered and has no expiry should answer empty")
    void findByIdempotencyKey_whenOperationHasNotAnsweredAndHasNoExpiry_shouldAnswerEmpty() {
        // given
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(container(null, null)));

        // when
        Optional<ResponseContainer> result = tested().findByIdempotencyKey(KEY);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(deserializer);
    }

    private DefaultResponseManager tested() {
        return new DefaultResponseManager(repository, serializer, deserializer, clock);
    }

    private static Response response() {
        return new DefaultResponse(201, new byte [] {1}, "application/json", Map.of());
    }

    private static RawResponseContainer container(String rawResponse) {
        return container(rawResponse, EXPIRES_AT);
    }

    private static RawResponseContainer container(String rawResponse, Instant expiresAt) {
        return new DefaultRawResponseContainer(rawResponse, "fingerprint", expiresAt);
    }
}
