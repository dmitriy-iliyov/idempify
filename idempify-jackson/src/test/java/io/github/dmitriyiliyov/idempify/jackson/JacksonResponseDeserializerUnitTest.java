package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.DeserializationException;
import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The mapper is a bare {@link ObjectMapper} here for the same reason as in
 * {@link JacksonResponseRoundTripUnitTest}: what the class has to hold is that a response is readable on the
 * mapper it was handed, and not only on one an application assembled.
 */
class JacksonResponseDeserializerUnitTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JacksonResponseDeserializer tested = new JacksonResponseDeserializer(mapper);

    @Test
    @DisplayName("UT constructor() when the mapper is null should throw NullPointerException")
    void constructor_whenMapperIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new JacksonResponseDeserializer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor() should leave the mapper it was handed as it was")
    void constructor_shouldLeaveMapperItWasHandedAsItWas() {
        // when / then
        assertThat(mapper.getDeserializationConfig().findMixInClassFor(DefaultResponse.class)).isNull();
    }

    @Test
    @DisplayName("UT deserialize() should read the stored text back into a response")
    void deserialize_shouldReadStoredTextBackIntoResponse() {
        // given
        String raw = """
                {"status":201,"body":"eyJwYWlkIjoxMH0=","contentType":"application/json",\
                "headers":{"Location":"/payments/1"}}""";

        // when
        Response result = tested.deserialize(raw);

        // then
        assertThat(result.getStatus()).isEqualTo(201);
        assertThat(result.getBody()).isEqualTo("{\"paid\":10}".getBytes(StandardCharsets.UTF_8));
        assertThat(result.getContentType()).isEqualTo("application/json");
        assertThat(result.getHeaders()).containsEntry("Location", "/payments/1");
    }

    @Test
    @DisplayName("UT deserialize() when the text is not a response should throw DeserializationException")
    void deserialize_whenTextIsNotResponse_shouldThrowDeserializationException() {
        // when / then
        assertThatThrownBy(() -> tested.deserialize("\"paid\""))
                .isInstanceOf(DeserializationException.class);
    }
}
