package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * All a handler is told about the conflict it has to resolve. It guards nothing on the way in - a handler
 * that produces no result of its own is handed no type either - and its {@code equals} is hand-written, so
 * both are what the cases below hold down.
 */
class DefaultConflictContextUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);

    @Test
    @DisplayName("UT getters() should return what the context was built from")
    void getters_shouldReturnWhatContextWasBuiltFrom() {
        // when
        ConflictContext tested = new DefaultConflictContext(KEY, RESULT_TYPE);

        // then
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(tested.getOperationResultType()).isEqualTo(RESULT_TYPE);
    }

    @Test
    @DisplayName("UT constructor() when the handler produces no result should accept a context without a type")
    void constructor_whenHandlerProducesNoResult_shouldAcceptContextWithoutType() {
        // when
        ConflictContext tested = new DefaultConflictContext(KEY, null);

        // then
        assertThat(tested.getOperationResultType()).isNull();
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("UT equals() when both values match should be equal and share a hash code")
    void equals_whenBothValuesMatch_shouldBeEqualAndShareHashCode() {
        // when / then
        assertThat(context()).isEqualTo(context());
        assertThat(context()).hasSameHashCodeAs(context());
    }

    @Test
    @DisplayName("UT equals() when the key differs should not be equal")
    void equals_whenKeyDiffers_shouldNotBeEqual() {
        // when / then
        assertThat(context()).isNotEqualTo(new DefaultConflictContext(OTHER_KEY, RESULT_TYPE));
    }

    @Test
    @DisplayName("UT equals() when the result type differs should not be equal")
    void equals_whenResultTypeDiffers_shouldNotBeEqual() {
        // when / then
        assertThat(context()).isNotEqualTo(new DefaultConflictContext(KEY, ResultType.ofClass(Integer.class)));
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        // given
        ConflictContext tested = context();

        // when / then
        assertThat(tested).isEqualTo(tested);
    }

    @Test
    @DisplayName("UT equals() when compared with null or another type should not be equal")
    void equals_whenComparedWithNullOrAnotherType_shouldNotBeEqual() {
        // when / then
        assertThat(context()).isNotEqualTo(null);
        assertThat(context()).isNotEqualTo(KEY);
    }

    @Test
    @DisplayName("UT toString() should name the key and the result type")
    void toString_shouldNameKeyAndResultType() {
        // when / then
        assertThat(context().toString())
                .contains("idempotencyKey=" + KEY)
                .contains("operationResultType=");
    }

    private static DefaultConflictContext context() {
        return new DefaultConflictContext(KEY, RESULT_TYPE);
    }
}
