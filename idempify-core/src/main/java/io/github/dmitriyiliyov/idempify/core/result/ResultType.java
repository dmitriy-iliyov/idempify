package io.github.dmitriyiliyov.idempify.core.result;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * The type a stored result is read back into, carried as a {@link Type} so that a parameterized return type
 * keeps its type arguments: a method returning {@code List<Order>} is replayed as a list of orders and not as
 * a list of maps.
 * <p>
 * Implementations of {@link ResultDeserializer} read {@link #getType()}.
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
