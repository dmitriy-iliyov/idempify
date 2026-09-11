package io.github.dmitriyiliyov.idempify.core.response;

/**
 * Renders a {@link Response} into the text the operation's record keeps.
 * <p>
 * Must round-trip with the {@link ResponseDeserializer} configured beside it. The body is arbitrary bytes
 * and the column is text, so an implementation has to encode it - the supplied Jackson pair leans on base64.
 */
public interface ResponseSerializer {

    /**
     * Serializes a response for storage.
     *
     * @param response the response to keep on the operation's record; never {@code null} - a record that has
     *                 not answered yet keeps an empty column, without asking a serializer.
     * @return the text to store; never {@code null}.
     */
    String serialize(Response response);
}
