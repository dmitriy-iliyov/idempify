package io.github.dmitriyiliyov.springidempotency.core;

public interface ResponseDeserializer {
    <T> T deserialize(String rawResponse, Class<T> c);
}
