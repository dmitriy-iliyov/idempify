package io.github.dmitriyiliyov.springidempotency.core;

public interface IdempotencyEventListener {

    void onDuplicate();

    void onException();

    void onSuccess();

    void onConflict();

    void onFingerprintMismatch();
}
