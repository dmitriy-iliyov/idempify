package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.SerializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JacksonResultSerializerUnitTest {

    @Test
    @DisplayName("UT serialize() when response is valid should return json string")
    void serialize_whenResponseIsValid_shouldReturnJsonString() throws Exception {
        // given
        ObjectMapper mapper = mock(ObjectMapper.class);
        JacksonResultSerializer tested = new JacksonResultSerializer(mapper);

        String response = "response";
        String expectedJson = "\"response\"";

        when(mapper.writeValueAsString(response))
                .thenReturn(expectedJson);

        // when
        String result = tested.serialize(response);

        // then
        assertThat(result)
                .isEqualTo(expectedJson);

        verify(mapper, times(1))
                .writeValueAsString(response);

        verifyNoMoreInteractions(mapper);
    }

    @Test
    @DisplayName("UT serialize() when mapper throws JsonProcessingException should throw SerializationException")
    void serialize_whenMapperThrowsJsonProcessingException_shouldThrowSerializationException() throws Exception {
        // given
        ObjectMapper mapper = mock(ObjectMapper.class);
        JacksonResultSerializer tested = new JacksonResultSerializer(mapper);

        String response = "response";

        JsonProcessingException exception = new JsonProcessingException("serialization error") {};

        when(mapper.writeValueAsString(response))
                .thenThrow(exception);

        // when / then
        assertThatThrownBy(() -> tested.serialize(response))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Error when serializing operation result")
                .hasCause(exception);

        verify(mapper, times(1))
                .writeValueAsString(response);

        verifyNoMoreInteractions(mapper);
    }
}