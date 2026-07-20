package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpRequestContextProviderUnitTest {

    @InjectMocks
    HttpRequestContextProvider tested;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("UT getContext() when request attributes are bound should return HttpRequestContext wrapping the current request")
    void getContext_whenRequestAttributesAreBound_shouldReturnHttpRequestContextWrappingCurrentRequest() {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(request.getMethod()).thenReturn("POST");

        // when
        RequestContext result = tested.getContext();

        // then
        assertThat(result).isInstanceOf(HttpRequestContext.class);
        assertThat(result.getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("UT getContext() when no request attributes are bound should throw IllegalStateException")
    void getContext_whenNoRequestAttributesAreBound_shouldThrowIllegalStateException() {
        // given
        RequestContextHolder.resetRequestAttributes();

        // when / then
        assertThatThrownBy(() -> tested.getContext())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requestAttributes is null");
    }
}
