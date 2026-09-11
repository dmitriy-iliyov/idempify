package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;

import java.util.Objects;

/**
 * Reads a stored response back into a {@link DefaultResponse}.
 * <p>
 * The mapper is not used as given: the text is read through a copy of it carrying {@link DefaultResponseMixin}.
 * Without that mixin the read succeeds only on a mapper somebody else registered {@code ParameterNamesModule}
 * on, so the circle this module promises would close on an assembly it does not own. The copy leaves the
 * caller's own mapper untouched.
 */
public class JacksonResponseDeserializer implements ResponseDeserializer {

    private final GenericJacksonDeserializer deserializer;

    public JacksonResponseDeserializer(ObjectMapper mapper) {
        ObjectMapper readingMapper = Objects.requireNonNull(mapper, "mapper cannot be null")
                .copy()
                .addMixIn(DefaultResponse.class, DefaultResponseMixin.class);
        this.deserializer = new GenericJacksonDeserializer(readingMapper);
    }

    @Override
    public Response deserialize(String rawResponse) {
        return (Response) deserializer.deserialize(rawResponse, DefaultResponse.class);
    }
}
