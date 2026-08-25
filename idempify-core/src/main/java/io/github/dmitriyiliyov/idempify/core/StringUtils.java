package io.github.dmitriyiliyov.idempify.core;

public final class StringUtils {

    private StringUtils() {}

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
