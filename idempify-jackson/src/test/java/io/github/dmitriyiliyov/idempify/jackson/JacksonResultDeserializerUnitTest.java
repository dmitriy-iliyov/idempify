package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.DeserializationException;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonResultDeserializerUnitTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JacksonResultDeserializer tested = new JacksonResultDeserializer(mapper);

    @Test
    @DisplayName("UT deserialize() when the type is a plain class should read the value back")
    void deserialize_whenTypeIsPlainClass_shouldReadValueBack() {
        // when
        Object result = tested.deserialize("{\"value\":\"test\"}", ResultType.ofClass(Order.class));

        // then
        assertThat(result).isEqualTo(new Order("test"));
    }

    @Test
    @DisplayName("UT deserialize() when the method returns a parameterized list should rebuild its elements")
    void deserialize_whenMethodReturnsParameterizedList_shouldRebuildItsElements() {
        // given
        ResultType type = ResultType.ofMethod(methodNamed("orders"));

        // when
        Object result = tested.deserialize("[{\"value\":\"first\"},{\"value\":\"second\"}]", type);

        // then
        assertThat(result)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Order.class))
                .containsExactly(new Order("first"), new Order("second"));
    }

    @Test
    @DisplayName("UT deserialize() when the method returns a parameterized map should rebuild its values")
    void deserialize_whenMethodReturnsParameterizedMap_shouldRebuildItsValues() {
        // given
        ResultType type = ResultType.ofMethod(methodNamed("ordersByName"));

        // when
        Object result = tested.deserialize("{\"a\":{\"value\":\"first\"}}", type);

        // then
        assertThat(result)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Order.class))
                .containsEntry("a", new Order("first"));
    }

    @Test
    @DisplayName("UT deserialize() when the type is a bare class should hand back untyped elements")
    void deserialize_whenTypeIsBareClass_shouldHandBackUntypedElements() {
        // when
        Object result = tested.deserialize("[{\"value\":\"first\"}]", ResultType.ofClass(List.class));

        // then
        assertThat(result)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .first()
                .isNotInstanceOf(Order.class)
                .isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("UT deserialize() when the stored result is null should hand back null")
    void deserialize_whenStoredResultIsNull_shouldHandBackNull() {
        // when
        Object result = tested.deserialize("null", ResultType.ofClass(Order.class));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT deserialize() when the stored result is malformed should wrap the failure with its cause")
    void deserialize_whenStoredResultIsMalformed_shouldWrapFailureWithItsCause() {
        // when / then
        assertThatThrownBy(() -> tested.deserialize("{not json", ResultType.ofClass(Order.class)))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("Error when deserializing operation result")
                .cause().isNotNull();
    }

    private static Method methodNamed(String name) {
        for (Method method : Signatures.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalStateException("no method named %s".formatted(name));
    }

    /**
     * Only the return types matter: {@link ResultType#ofMethod} reads them, and they are the reason the
     * descriptor carries a {@code Type} rather than a {@code Class}.
     */
    @SuppressWarnings("unused")
    private static final class Signatures {

        static List<Order> orders() {
            return List.of();
        }

        static Map<String, Order> ordersByName() {
            return Map.of();
        }
    }

    private record Order(String value) {}
}
