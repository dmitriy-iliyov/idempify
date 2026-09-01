package io.github.dmitriyiliyov.idempify.core;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * The type a stored result is read back into, carried as a {@link Type} rather than a {@link Class} so that
 * a parameterized return type survives the trip.
 * <p>
 * {@code Class} was the obvious carrier and the wrong one: it has no room for type arguments, so a method
 * returning {@code List<Order>} arrived at the deserializer as a bare {@code List} and came back as a list of
 * maps. The information was never lost to erasure - a generic return type is written into the class file and
 * {@link Method#getGenericReturnType()} reads it back - it was lost to the descriptor this library chose.
 * <p>
 * Implementations of {@link ResultDeserializer} should read {@link #getType()}: it is what a serialization
 * library needs and what {@code Class} could not express.
 */
public final class ResultType {

    private final Type type;

    private ResultType(Type type) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    /**
     * Takes the type from a method's declared return type, keeping its type arguments.
     */
    public static ResultType ofMethod(Method method) {
        Objects.requireNonNull(method, "method cannot be null");
        return new ResultType(method.getGenericReturnType());
    }

    /**
     * Takes the type from a class, for a caller that knows it outright rather than through reflection.
     */
    public static ResultType ofClass(Class<?> c) {
        Objects.requireNonNull(c, "c cannot be null");
        return new ResultType(c);
    }

    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResultType that)) {
            return false;
        }
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return "ResultType{" +
                "type=" + type.getTypeName() +
                '}';
    }
}
