package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Parsing of idempotency keys, shared by every transport that receives them as text.
 */
public final class UuidUtils {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private UuidUtils() {}

    public static boolean isCanonical(String value) {
        return value != null && CANONICAL_UUID.matcher(value).matches();
    }

    public static UUID parseCanonical(String value) {
        return isCanonical(value) ? UUID.fromString(value) : null;
    }
}
