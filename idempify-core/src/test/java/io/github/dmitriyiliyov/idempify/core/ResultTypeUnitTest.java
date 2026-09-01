package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTypeUnitTest {

    @Test
    @DisplayName("UT ofMethod() when method is null should throw NullPointerException")
    void ofMethod_whenMethodIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ResultType.ofMethod(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("method cannot be null");
    }

    @Test
    @DisplayName("UT ofClass() when class is null should throw NullPointerException")
    void ofClass_whenClassIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ResultType.ofClass(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("c cannot be null");
    }

    @Test
    @DisplayName("UT ofMethod() when the return type is parameterized should keep its type arguments")
    void ofMethod_whenReturnTypeIsParameterized_shouldKeepItsTypeArguments() {
        // when
        ResultType tested = ResultType.ofMethod(methodNamed("orders"));

        // then
        assertThat(tested.getType()).isInstanceOf(ParameterizedType.class);
        ParameterizedType type = (ParameterizedType) tested.getType();
        assertThat(type.getRawType()).isEqualTo(List.class);
        assertThat(type.getActualTypeArguments()).containsExactly(Order.class);
    }

    @Test
    @DisplayName("UT ofMethod() when the return type is nested should keep every argument down the tree")
    void ofMethod_whenReturnTypeIsNested_shouldKeepEveryArgumentDownTheTree() {
        // when
        ResultType tested = ResultType.ofMethod(methodNamed("ordersByName"));

        // then
        assertThat(tested.getType().getTypeName())
                .contains("java.util.Map")
                .contains("java.util.List")
                .contains(Order.class.getName());
    }

    @Test
    @DisplayName("UT ofMethod() when the return type is plain should give back that very class")
    void ofMethod_whenReturnTypeIsPlain_shouldGiveBackThatVeryClass() {
        assertThat(ResultType.ofMethod(methodNamed("order")).getType()).isEqualTo(Order.class);
    }

    @Test
    @DisplayName("UT ofMethod() when the method returns void should give back the void type")
    void ofMethod_whenMethodReturnsVoid_shouldGiveBackVoidType() {
        assertThat(ResultType.ofMethod(methodNamed("nothing")).getType()).isEqualTo(void.class);
    }

    @Test
    @DisplayName("UT ofMethod() and ofClass() on the same parameterized return should disagree")
    void ofMethodAndOfClass_onSameParameterizedReturn_shouldDisagree() {
        // given - the very difference the descriptor exists for: one keeps the arguments, one cannot hold them
        ResultType fromMethod = ResultType.ofMethod(methodNamed("orders"));
        ResultType fromClass = ResultType.ofClass(List.class);

        // then
        assertThat(fromMethod).isNotEqualTo(fromClass);
        assertThat(fromMethod.getType().getTypeName()).contains(Order.class.getName());
        assertThat(fromClass.getType().getTypeName()).doesNotContain(Order.class.getName());
    }

    @Test
    @DisplayName("UT equals() when built from the same class twice should treat the descriptors as equal")
    void equals_whenBuiltFromSameClassTwice_shouldTreatDescriptorsAsEqual() {
        // given - the contexts that hold a descriptor compare by it, so equality cannot be by identity
        assertThat(ResultType.ofClass(Order.class)).isEqualTo(ResultType.ofClass(Order.class));
        assertThat(ResultType.ofClass(Order.class)).hasSameHashCodeAs(ResultType.ofClass(Order.class));
    }

    @Test
    @DisplayName("UT equals() when built from the same method twice should treat the descriptors as equal")
    void equals_whenBuiltFromSameMethodTwice_shouldTreatDescriptorsAsEqual() {
        assertThat(ResultType.ofMethod(methodNamed("orders")))
                .isEqualTo(ResultType.ofMethod(methodNamed("orders")));
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        ResultType tested = ResultType.ofClass(Order.class);
        assertThat(tested).isEqualTo(tested);
    }

    @Test
    @DisplayName("UT equals() when the described types differ should tell the descriptors apart")
    void equals_whenDescribedTypesDiffer_shouldTellDescriptorsApart() {
        assertThat(ResultType.ofClass(Order.class)).isNotEqualTo(ResultType.ofClass(String.class));
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(ResultType.ofClass(Order.class)).isNotEqualTo(Order.class);
    }

    @Test
    @DisplayName("UT toString() should name the described type in full")
    void toString_shouldNameDescribedTypeInFull() {
        assertThat(ResultType.ofMethod(methodNamed("orders")).toString())
                .isEqualTo("ResultType{type=java.util.List<%s>}".formatted(Order.class.getName()));
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
     * Declared for their return types alone - that is the only thing {@link ResultType#ofMethod} reads.
     */
    @SuppressWarnings("unused")
    private static final class Signatures {

        static List<Order> orders() {
            return List.of();
        }

        static Map<String, List<Order>> ordersByName() {
            return Map.of();
        }

        static Order order() {
            return null;
        }

        static void nothing() { }
    }

    private record Order(String value) {}
}
