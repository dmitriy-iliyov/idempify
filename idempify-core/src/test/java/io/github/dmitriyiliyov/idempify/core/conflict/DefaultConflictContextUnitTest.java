package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConflictContextUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    @DisplayName("UT getters should hand back the key and the result type the context was built with")
    void getters_shouldHandBackKeyAndResultTypeContextWasBuiltWith() {
        // given
        DefaultConflictContext tested = new DefaultConflictContext(KEY, ResultType.ofClass(String.class));

        // then
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(tested.getOperationResultType()).isEqualTo(ResultType.ofClass(String.class));
    }

    @Test
    @DisplayName("UT equals() when two contexts describe the same conflict should treat them as equal")
    void equals_whenTwoContextsDescribeSameConflict_shouldTreatThemAsEqual() {
        // given
        DefaultConflictContext one = new DefaultConflictContext(KEY, ResultType.ofClass(String.class));
        DefaultConflictContext other = new DefaultConflictContext(KEY, ResultType.ofClass(String.class));

        // then
        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when the key differs should treat the contexts as different")
    void equals_whenKeyDiffers_shouldTreatContextsAsDifferent() {
        assertThat(new DefaultConflictContext(KEY, ResultType.ofClass(String.class)))
                .isNotEqualTo(new DefaultConflictContext(OTHER_KEY, ResultType.ofClass(String.class)));
    }

    @Test
    @DisplayName("UT equals() when the result type differs should treat the contexts as different")
    void equals_whenResultTypeDiffers_shouldTreatContextsAsDifferent() {
        assertThat(new DefaultConflictContext(KEY, ResultType.ofClass(String.class)))
                .isNotEqualTo(new DefaultConflictContext(KEY, ResultType.ofClass(Integer.class)));
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        DefaultConflictContext tested = new DefaultConflictContext(KEY, ResultType.ofClass(String.class));
        assertThat(tested).isEqualTo(tested);
    }

    @Test
    @DisplayName("UT equals() when compared to another type should not be equal")
    void equals_whenComparedToAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultConflictContext(KEY, ResultType.ofClass(String.class))).isNotEqualTo("not a context");
    }

    @Test
    @DisplayName("UT toString() should name the key and the result type")
    void toString_shouldNameKeyAndResultType() {
        assertThat(new DefaultConflictContext(KEY, ResultType.ofClass(String.class)).toString())
                .contains(KEY.toString(), "java.lang.String");
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultConflictContext(
                java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), ResultType.ofClass(String.class)))
                .isNotEqualTo("not a context");
    }
}
