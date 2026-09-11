package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The circle a stored operation makes: domain object -> {@link RawOperation} -> domain object, on the live
 * serializers this module supplies. A row is written once and read back on every repeat, so neither half
 * means anything on its own - what has to hold is that the operation comes back the same.
 * <p>
 * The mapper is a bare {@link ObjectMapper} on purpose, for the reason spelled out in
 * {@link JacksonResponseRoundTripUnitTest}: in an application one arrives from Boot already carrying a
 * {@code ParameterNamesModule}, and a circle that closes only on somebody else's assembly is not closed.
 */
class OperationSerializationComponentTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant CREATED_AT = Instant.parse("2026-08-09T12:00:00Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(3600);

    private final ObjectMapper mapper = new ObjectMapper();
    private final OperationSerializer serializer = new DefaultOperationSerializer(
            new JacksonResultSerializer(mapper),
            new GenericJacksonSerializer(mapper)::serialize
    );
    private final OperationDeserializer deserializer = new DefaultOperationDeserializer(
            new JacksonResultDeserializer(mapper),
            new JacksonResponseDeserializer(mapper)
    );

    @Test
    @DisplayName("CT round trip when the result is a scalar should read it back unchanged")
    void roundTrip_whenResultIsScalar_shouldReadItBackUnchanged() {
        // given
        ResultType resultType = ResultType.ofClass(String.class);
        Operation written = processed("paid", resultType, null);

        // when
        Operation result = roundTrip(written, resultType);

        // then
        assertThat(result.getResult()).isEqualTo("paid");
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
        assertThat(result.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("CT round trip when the result is a parameterized list should rebuild its elements")
    void roundTrip_whenResultIsParameterizedList_shouldRebuildItsElements() {
        // given
        ResultType resultType = ResultType.ofMethod(methodNamed("orders"));
        Operation written = processed(List.of(new Order("first"), new Order("second")), resultType, null);

        // when
        Operation result = roundTrip(written, resultType);

        // then
        assertThat(result.getResult())
                .asInstanceOf(InstanceOfAssertFactories.list(Order.class))
                .containsExactly(new Order("first"), new Order("second"));
    }

    @Test
    @DisplayName("CT round trip when the operation carries a response should read back its body and headers")
    void roundTrip_whenOperationCarriesResponse_shouldReadBackItsBodyAndHeaders() {
        // given
        ResultType resultType = ResultType.ofClass(String.class);
        Response response = new DefaultResponse(
                201,
                "{\"paid\":10}".getBytes(StandardCharsets.UTF_8),
                "application/json",
                Map.of("Location", "/payments/1")
        );
        Operation written = processed("paid", resultType, response);

        // when
        Operation result = roundTrip(written, resultType);

        // then
        assertThat(result.getResponse()).isNotNull();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getBody()).isEqualTo(response.getBody());
        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        assertThat(result.getResponse().getHeaders()).containsEntry("Location", "/payments/1");
    }

    @Test
    @DisplayName("CT round trip when the operation carries no response should leave that column empty")
    void roundTrip_whenOperationCarriesNoResponse_shouldLeaveThatColumnEmpty() {
        // given
        ResultType resultType = ResultType.ofClass(String.class);
        Operation written = processed("paid", resultType, null);

        // when
        RawOperation raw = serializer.serialize(written);
        Operation result = deserializer.deserialize(raw, resultType);

        // then
        assertThat(raw.response()).isNull();
        assertThat(result.getResponse()).isNull();
        assertThat(result.getResult()).isEqualTo("paid");
    }

    @Test
    @DisplayName("CT round trip when the operation has no expiry should read it back without one")
    void roundTrip_whenOperationHasNoExpiry_shouldReadItBackWithoutOne() {
        // given
        ResultType resultType = ResultType.ofClass(String.class);
        Operation written = new Operation(
                KEY, OperationStatus.IN_PROCESS, true, null, resultType, null, "fingerprint", null, CREATED_AT);

        // when
        RawOperation raw = serializer.serialize(written);
        Operation result = deserializer.deserialize(raw, resultType);

        // then
        assertThat(raw.expiresAt()).isNull();
        assertThat(result.getExpiresAt()).isNull();
        assertThat(result.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
    }

    @Test
    @DisplayName("CT round trip when no result type is given should keep the column but not read the result at all")
    void roundTrip_whenNoResultTypeIsGiven_shouldKeepColumnButNotReadResultAtAll() {
        // given
        Operation written = processed("paid", ResultType.ofClass(String.class), null);

        // when
        RawOperation raw = serializer.serialize(written);
        Operation result = deserializer.deserialize(raw, null);

        // then
        assertThat(raw.result()).isEqualTo("\"paid\"");
        assertThat(result.getResult()).isNull();
        assertThat(result.getResultType()).isNull();
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
    }

    private Operation roundTrip(Operation written, ResultType resultType) {
        return deserializer.deserialize(serializer.serialize(written), resultType);
    }

    private Operation processed(Object result, ResultType resultType, Response response) {
        return new Operation(
                KEY,
                OperationStatus.PROCESSED,
                true,
                result,
                resultType,
                response,
                "fingerprint",
                EXPIRES_AT,
                CREATED_AT
        );
    }

    private static Method methodNamed(String name) {
        for (Method method : Signatures.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalStateException("no method named %s".formatted(name));
    }

    /**
     * Only the return type matters: it is what {@link ResultType#ofMethod} reads to keep the type arguments
     * a raw column cannot carry.
     */
    @SuppressWarnings("unused")
    private static final class Signatures {

        static List<Order> orders() {
            return List.of();
        }
    }

    private record Order(String value) {}
}
