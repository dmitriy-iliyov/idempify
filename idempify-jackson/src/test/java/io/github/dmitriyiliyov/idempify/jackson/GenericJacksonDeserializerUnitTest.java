package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.DeserializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reading half of the same pair. An empty column is the ordinary answer for a record that has not
 * answered yet, so it must not reach the mapper at all; text the mapper cannot read is a failure.
 */
class GenericJacksonDeserializerUnitTest {

    private final GenericJacksonDeserializer tested = new GenericJacksonDeserializer(new ObjectMapper());

    @Test
    @DisplayName("UT constructor() when the mapper is null should throw NullPointerException")
    void constructor_whenMapperIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new GenericJacksonDeserializer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper cannot be null");
    }

    @Test
    @DisplayName("UT deserialize() should read the text back into the type it was given")
    void deserialize_shouldReadTextBackIntoTypeItWasGiven() {
        // when / then
        assertThat(tested.deserialize("\"paid\"", String.class)).isEqualTo("paid");
    }

    @Test
    @DisplayName("UT deserialize() when the column is empty should answer null without asking the mapper")
    void deserialize_whenColumnIsEmpty_shouldAnswerNullWithoutAskingMapper() {
        // when / then
        assertThat(tested.deserialize(null, String.class)).isNull();
    }

    @Test
    @DisplayName("UT deserialize() when the text is not the type it was given should throw DeserializationException")
    void deserialize_whenTextIsNotTypeItWasGiven_shouldThrowDeserializationException() {
        // when / then
        assertThatThrownBy(() -> tested.deserialize("{\"paid\":10}", Integer.class))
                .isInstanceOf(DeserializationException.class)
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
}
