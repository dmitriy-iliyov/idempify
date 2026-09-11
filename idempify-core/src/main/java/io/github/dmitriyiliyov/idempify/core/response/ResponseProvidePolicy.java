package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Set;

/**
 * Which response headers survive onto the record, for a {@link ResponseProvider} to apply while it collects
 * the response.
 * <p>
 * An absent or empty {@link #getIncludedHeaders()} keeps every header the response carries; a non-empty one
 * keeps only those named. {@link #getExcludedHeaders()} is subtracted afterwards in either case, so a header
 * named in both sets is dropped.
 * <p>
 * Both sets name headers in lower case. HTTP header names are case-insensitive, so the match is made against
 * the response's own name lowercased - a name held here in any other case matches nothing.
 */
public interface ResponseProvidePolicy {
    Set<String> getIncludedHeaders();
    Set<String> getExcludedHeaders();
}
