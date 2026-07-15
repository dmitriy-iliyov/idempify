package io.github.dmitriyiliyov.springidempotency.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JacksonResponseDeserializerUnitTests {

    @Test
    @DisplayName("UT deserialize() when raw response is valid should return object")
    void deserialize_whenRawResponseIsValid_shouldReturnObject() throws Exception {
        // given
        ObjectMapper mapper = mock(ObjectMapper.class);
        JacksonResponseDeserializer tested = new JacksonResponseDeserializer(mapper);

        String rawResponse = "{\"value\":\"test\"}";
        TestResponse expectedResponse = new TestResponse("test");

        when(mapper.readValue(rawResponse, TestResponse.class))
                .thenReturn(expectedResponse);

        // when
        TestResponse result = tested.deserialize(rawResponse, TestResponse.class);

        // then
        assertThat(result)
                .isEqualTo(expectedResponse);

        verify(mapper, times(1))
                .readValue(rawResponse, TestResponse.class);

        verifyNoMoreInteractions(mapper);
    }

    @Test
    @DisplayName("UT deserialize() when mapper throws JsonProcessingException should throw RuntimeException")
    void deserialize_whenMapperThrowsJsonProcessingException_shouldThrowRuntimeException() throws Exception {
        // given
        ObjectMapper mapper = mock(ObjectMapper.class);
        JacksonResponseDeserializer tested = new JacksonResponseDeserializer(mapper);

        String rawResponse = "{\"value\":\"test\"}";

        JsonProcessingException exception = new JsonProcessingException("deserialization error") {};

        when(mapper.readValue(rawResponse, TestResponse.class))
                .thenThrow(exception);

        // when / then
        assertThatThrownBy(() -> tested.deserialize(rawResponse, TestResponse.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error when deserializing operation response")
                .hasCause(exception);

        verify(mapper, times(1))
                .readValue(rawResponse, TestResponse.class);

        verifyNoMoreInteractions(mapper);
    }

    private record TestResponse(String value) {}
}