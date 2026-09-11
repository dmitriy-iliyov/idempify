package io.github.dmitriyiliyov.idempify.core.response;

import java.time.Instant;
import java.util.Objects;

public final class DefaultRawResponseContainer implements RawResponseContainer {

    private final String response;
    private final String fingerprint;
    private final Instant expiresAt;

    public DefaultRawResponseContainer(String response, String fingerprint, Instant expiresAt) {
        this.response = response;
        this.fingerprint = fingerprint;
        this.expiresAt = expiresAt;
    }

    @Override
    public String getResponse() {
        return response;
    }

    @Override
    public String getFingerprint() {
        return fingerprint;
    }

    @Override
    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultRawResponseContainer that)) {
            return false;
        }
        return Objects.equals(response, that.response)
                && Objects.equals(fingerprint, that.fingerprint)
                && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(response, fingerprint, expiresAt);
    }

    @Override
    public String toString() {
        return "DefaultRawResponseContainer{" +
                "hasResponse=" + (response != null) +
                ", fingerprint='" + fingerprint + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
