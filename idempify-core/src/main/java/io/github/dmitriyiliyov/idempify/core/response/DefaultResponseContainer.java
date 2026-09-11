package io.github.dmitriyiliyov.idempify.core.response;

public final class DefaultResponseContainer implements ResponseContainer {

    private final Response response;
    private final String fingerprint;

    public DefaultResponseContainer(Response response, String fingerprint) {
        this.response = response;
        this.fingerprint = fingerprint;
    }

    @Override
    public Response getResponse() {
        return response;
    }

    @Override
    public String getFingerprint() {
        return fingerprint;
    }

    @Override
    public String toString() {
        return "DefaultResponseContainer{" +
                "response=" + response +
                ", fingerprint='" + fingerprint + '\'' +
                '}';
    }
}
