package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.SerializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pair behind every non-result value the library stores. Its whole contract is the three answers below:
 * nothing in gives nothing out, an object gives its JSON, and a value Jackson cannot walk is a failure rather
 * than an empty column.
 */
class GenericJacksonSerializerUnitTest {

    private final GenericJacksonSerializer tested = new GenericJacksonSerializer(new ObjectMapper());

    @Test
    @DisplayName("UT constructor() when the mapper is null should throw NullPointerException naming the parameter")
    void constructor_whenMapperIsNull_shouldThrowNullPointerExceptionNamingParameter() {
        // when / then
        assertThatThrownBy(() -> new GenericJacksonSerializer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper cannot be null");
    }

    @Test
    @DisplayName("UT serialize() should render the object as JSON")
    void serialize_shouldRenderObjectAsJson() {
        // when / then
        assertThat(tested.serialize("paid")).isEqualTo("\"paid\"");
    }

    @Test
    @DisplayName("UT serialize() when Jackson cannot walk the object should throw SerializationException")
    void serialize_whenJacksonCannotWalkObject_shouldThrowSerializationException() {
        // when / then
        assertThatThrownBy(() -> tested.serialize(new Unserializable()))
                .isInstanceOf(SerializationException.class)
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    /**
     * A getter Jackson has no serializer for, which is the cheapest way to reach the failure branch without
     * a mock in the way.
     */
    private static final class Unserializable {

        public OutputStream getStream() {
            return OutputStream.nullOutputStream();
        }
    }
}
