package io.github.dmitriyiliyov.springidempotency.core;

public interface ResponseSerializer {
    <T> String serialize(T response);
}
