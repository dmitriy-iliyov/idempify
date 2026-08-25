package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.IdempotencyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class DefaultFingerprintMatcher implements FingerprintMatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultFingerprintMatcher.class);
    private final IdempotencyEventListener eventListener;

    public DefaultFingerprintMatcher(IdempotencyEventListener eventListener) {
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener cannot be null");
    }

    @Override
    public void match(String current, String previous, FingerprintPolicy policy, UUID idempotencyKey) {
        log.debug("Comparing operation (idempotencyKey={}) fingerprint", idempotencyKey);

        boolean fingerprintsMatch = policy.match(
                previous,
                current
        );

        if (!fingerprintsMatch) {
            log.debug("Fingerprint mismatch for operation (idempotencyKey={}), start handling", idempotencyKey);

            eventListener.onFingerprintMismatch();

            FingerprintMismatchContext mismatchContext = new DefaultFingerprintMismatchContext(
                    idempotencyKey,
                    previous,
                    current
            );

            policy.handle(mismatchContext);
        }
    }
}
