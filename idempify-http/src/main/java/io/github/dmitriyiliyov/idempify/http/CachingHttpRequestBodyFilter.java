package io.github.dmitriyiliyov.idempify.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

public class CachingHttpRequestBodyFilter extends OncePerRequestFilter {

    private final IdempotentEndpointRegistry endpointRegistry;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public CachingHttpRequestBodyFilter(IdempotentEndpointRegistry endpointRegistry) {
        this.endpointRegistry = Objects.requireNonNull(endpointRegistry, "endpointRegistry cannot be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(new CachedHttpServletRequestWrapper(request), response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();
        return endpointRegistry.getPatterns()
                .stream()
                .noneMatch(pattern -> matcher.match(pattern, uri));
    }
}
