package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The response is stored as text and replayed from it, so what matters is the round trip rather than either
 * half: the module has to read back what it wrote, on the mapper it is given.
 * <p>
 * The mapper here is a bare {@link ObjectMapper} on purpose. In an application one arrives from Boot with a
 * {@code ParameterNamesModule} already registered, and a circle that closes only on somebody else's assembly
 * is not closed.
 */
class JacksonResponseRoundTripUnitTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GenericJacksonSerializer serializer = new GenericJacksonSerializer(mapper);
    private final JacksonResponseDeserializer deserializer = new JacksonResponseDeserializer(mapper);

    @Test
    @DisplayName("UT round trip when the response carries a body should read it back unchanged")
    void roundTrip_whenResponseCarriesBody_shouldReadItBackUnchanged() {
        // given
        Response written = new DefaultResponse(
                201,
                "{\"paid\":10}".getBytes(StandardCharsets.UTF_8),
                "application/json",
                Map.of("Location", "/payments/1")
        );

        // when
        Response result = readBack(serializer.serialize(written));

        // then
        assertThat(result.getStatus()).isEqualTo(201);
        assertThat(result.getBody()).isEqualTo(written.getBody());
        assertThat(result.getContentType()).isEqualTo("application/json");
        assertThat(result.getHeaders()).containsEntry("Location", "/payments/1");
    }

    @Test
    @DisplayName("UT round trip when the response carries no body should read it back empty")
    void roundTrip_whenResponseCarriesNoBody_shouldReadItBackEmpty() {
        // given
        Response written = new DefaultResponse(204, new byte [0], null, Map.of());

        // when
        Response result = readBack(serializer.serialize(written));

        // then
        assertThat(result.getStatus()).isEqualTo(204);
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    @DisplayName("UT serialize() should write the body as base64 so arbitrary bytes survive a text column")
    void serialize_shouldWriteBodyAsBase64SoArbitraryBytesSurviveTextColumn() {
        // given
        Response written = new DefaultResponse(
                201, new byte [] {0, 1, 2}, "application/octet-stream", Map.of());

        // when
        String raw = serializer.serialize(written);

        // then
        assertThat(raw).contains("\"body\":\"AAEC\"");
    }

    private Response readBack(String raw) {
        return deserializer.deserialize(raw);
    }
}
