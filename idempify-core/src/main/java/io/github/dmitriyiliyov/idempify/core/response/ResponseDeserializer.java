package io.github.dmitriyiliyov.idempify.core.response;

/**
 * Reads back what a {@link ResponseSerializer} wrote, into a {@link Response} a transport can replay as it
 * stands.
 * <p>
 * Which concrete type carries it is the implementation's business: nothing past this interface reads more
 * than {@link Response} declares.
 */
public interface ResponseDeserializer {

    /**
     * Deserializes a stored response.
     *
     * @param rawResponse what {@link ResponseSerializer} wrote for this operation; never {@code null} - an
     *                    empty column is read back as no response, without asking a deserializer.
     * @return the response the serializer was given; never {@code null}.
     */
    Response deserialize(String rawResponse);
}
