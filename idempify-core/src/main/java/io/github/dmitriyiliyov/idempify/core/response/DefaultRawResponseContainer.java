package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Objects;

public final class DefaultRawResponseContainer implements RawResponseContainer {

    private final String response;
    private final String fingerprint;
    private final Long remainingMillis;

    public DefaultRawResponseContainer(String response, String fingerprint, Long remainingMillis) {
        this.response = response;
        this.fingerprint = fingerprint;
        this.remainingMillis = remainingMillis;
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
    public Long getRemainingMillis() {
        return remainingMillis;
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
                && Objects.equals(remainingMillis, that.remainingMillis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(response, fingerprint, remainingMillis);
    }

    @Override
    public String toString() {
        return "DefaultRawResponseContainer{" +
                "hasResponse=" + (response != null) +
                ", fingerprint='" + fingerprint + '\'' +
                ", remainingMillis=" + remainingMillis +
                '}';
    }
}
