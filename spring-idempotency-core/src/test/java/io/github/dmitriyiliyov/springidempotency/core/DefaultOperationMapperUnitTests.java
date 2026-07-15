package io.github.dmitriyiliyov.springidempotency.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DefaultOperationMapperUnitTests {

    private final DefaultOperationMapper tested = new DefaultOperationMapper();

    @Test
    @DisplayName("UT toOperation() when valid metadata should correctly map to Operation")
    void toOperation_whenValidMetadata_shouldMapCorrectly() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        UUID idempotencyKey = UUID.randomUUID();
        String fingerprint = "test-fingerprint";
        Instant now = Instant.now();
        long ttl = 30L;
        TimeUnit timeUnit = TimeUnit.MINUTES;

        when(metadata.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(metadata.getTtl()).thenReturn(ttl);
        when(metadata.getTimeUnit()).thenReturn(timeUnit);
        when(metadata.getFingerprint()).thenReturn(fingerprint);

        Instant expectedExpiresAt = now.plusSeconds(1800);

        // when
        Operation result = tested.toOperation(metadata, now);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(result.getState()).isEqualTo(OperationState.IN_PROCESS);
        assertThat(result.getResponse()).isNull();
        assertThat(result.getFingerprint()).isEqualTo(fingerprint);
        assertThat(result.getExpiresAt()).isEqualTo(expectedExpiresAt);
        assertThat(result.getCreatedAt()).isEqualTo(now);
    }
}