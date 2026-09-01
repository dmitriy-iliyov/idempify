package io.github.dmitriyiliyov.idempify.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the published error identifiers. A client branches on these strings, so a rename is a breaking change
 * and has to be a deliberate edit of this test, not a side effect of refactoring a constant.
 */
class ProblemTypesUnitTest {

    @Test
    @DisplayName("UT types should keep the identifiers clients branch on")
    void types_shouldKeepIdentifiersClientsBranchOn() {
        assertThat(ProblemTypes.INVALID_IDEMPOTENCY_KEY)
                .isEqualTo(URI.create("https://idempify.io/errors/invalid-idempotency-key"));
        assertThat(ProblemTypes.EMPTY_REQUEST_BODY)
                .isEqualTo(URI.create("https://idempify.io/errors/empty-request-body"));
        assertThat(ProblemTypes.OPERATION_IN_PROCESS)
                .isEqualTo(URI.create("https://idempify.io/errors/operation-in-process"));
        assertThat(ProblemTypes.IDEMPOTENCY_KEY_REUSE)
                .isEqualTo(URI.create("https://idempify.io/errors/idempotency-key-reuse"));
        assertThat(ProblemTypes.OPERATION_NOT_COMPLETED)
                .isEqualTo(URI.create("https://idempify.io/errors/operation-not-completed"));
        assertThat(ProblemTypes.FINGERPRINT_POLICY_BROKEN)
                .isEqualTo(URI.create("https://idempify.io/errors/fingerprint-policy-broken"));
        assertThat(ProblemTypes.IDEMPOTENT_PROCESSING_FAILED)
                .isEqualTo(URI.create("https://idempify.io/errors/idempotent-processing-failed"));
        assertThat(ProblemTypes.RESULT_PROCESSING_FAILED)
                .isEqualTo(URI.create("https://idempify.io/errors/result-processing-failed"));
    }

    @Test
    @DisplayName("UT types should all be absolute so that they do not resolve against the request pattern")
    void types_shouldAllBeAbsoluteSoThatTheyDoNotResolveAgainstRequestUri() {
        assertThat(publishedTypes()).allSatisfy(type -> assertThat(type.isAbsolute()).isTrue());
    }

    @Test
    @DisplayName("UT types should be distinct so that two failures never share an identifier by accident")
    void types_shouldBeDistinctSoThatTwoFailuresNeverShareIdentifierByAccident() {
        List<URI> types = publishedTypes();

        assertThat(types).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("UT class should not be instantiable")
    void class_shouldNotBeInstantiable() throws Exception {
        Constructor<ProblemTypes> constructor = ProblemTypes.class.getDeclaredConstructor();

        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(ProblemTypes.class.getModifiers())).isTrue();
    }

    private static List<URI> publishedTypes() {
        return Arrays.stream(ProblemTypes.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> field.getType() == URI.class)
                .map(ProblemTypesUnitTest::valueOf)
                .toList();
    }

    private static URI valueOf(Field field) {
        try {
            return (URI) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
