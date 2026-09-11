package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.response.ResponseProvidePolicy;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class HttpResponseProviderUtils {

    private HttpResponseProviderUtils() { }

    public static Map<String, String> filterHeaders(ResponseProvidePolicy policy,
                                                    HttpServletResponse response) {
        Set<String> headerNames = new LinkedHashSet<>();

        Set<String> included = orEmpty(policy.getIncludedHeaders());
        Set<String> excluded = orEmpty(policy.getExcludedHeaders());

        if (included.isEmpty()) {
            headerNames.addAll(response.getHeaderNames());
        }

        for (String name : response.getHeaderNames()) {
            if (included.contains(name.toLowerCase())) {
                headerNames.add(name);
            }

            if (excluded.contains(name.toLowerCase())) {
                headerNames.remove(name);
            }
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headerNames.forEach(name -> headers.put(name, response.getHeader(name)));
        return headers;
    }

    private static Set<String> orEmpty(Set<String> names) {
        return names == null ? Set.of() : names;
    }
}
