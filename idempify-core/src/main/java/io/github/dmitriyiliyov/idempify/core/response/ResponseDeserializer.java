package io.github.dmitriyiliyov.idempify.core.response;

/**
 * Reads back what a {@link ResponseSerializer} wrote, into a {@link Response} a transport can replay as it
 * stands.
 * <p>
 * Which concrete type carries it is the implementation's business: nothing past this interface reads more
 * than {@link Response} declares.
 */
public interface ResponseDeserializer {
    Response deserialize(String rawResponse);
}
