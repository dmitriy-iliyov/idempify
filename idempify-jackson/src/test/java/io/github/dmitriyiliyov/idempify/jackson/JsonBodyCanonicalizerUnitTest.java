package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonBodyCanonicalizerUnitTest {

    private static final String MISSING_FIELD = "\0missing";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BodyCanonicalizerConfig.Builder completeConfig() {
        return BodyCanonicalizerConfig.builder(BodyCanonicalizerConfig.defaults());
    }

    private String canonicalize(String json) {
        return canonicalize(BodyCanonicalizerConfig.defaults(), json);
    }

    private String canonicalize(BodyCanonicalizerConfig config, String json) {
        return new JsonBodyCanonicalizer(config, objectMapper)
                .canonicalize(json.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("whole body")
    class WholeBody {

        @Test
        @DisplayName("UT canonicalize() when keys are in a different order should return the same form")
        void canonicalize_whenKeysInDifferentOrder_shouldReturnSameForm() {
            // given
            String first = "{\"b\":2,\"a\":1}";
            String second = "{\"a\":1,\"b\":2}";

            // when
            String firstResult = canonicalize(first);
            String secondResult = canonicalize(second);

            // then
            assertThat(firstResult).isEqualTo("{\"a\":1,\"b\":2}");
            assertThat(firstResult).isEqualTo(secondResult);
        }

        @Test
        @DisplayName("UT canonicalize() when nested keys are in a different order should return the same form")
        void canonicalize_whenNestedKeysInDifferentOrder_shouldReturnSameForm() {
            // given
            String first = "{\"outer\":{\"z\":1,\"a\":2}}";
            String second = "{\"outer\":{\"a\":2,\"z\":1}}";

            // when
            String firstResult = canonicalize(first);
            String secondResult = canonicalize(second);

            // then
            assertThat(firstResult).isEqualTo("{\"outer\":{\"a\":2,\"z\":1}}");
            assertThat(firstResult).isEqualTo(secondResult);
        }

        @Test
        @DisplayName("UT canonicalize() when whitespace differs should return the same form")
        void canonicalize_whenWhitespaceDiffers_shouldReturnSameForm() {
            // given
            String spaced = "{\n  \"a\" : 1,\n  \"b\" : 2\n}";
            String compact = "{\"a\":1,\"b\":2}";

            // when / then
            assertThat(canonicalize(spaced)).isEqualTo(canonicalize(compact));
        }

        @Test
        @DisplayName("UT canonicalize() when array order differs should return a different form")
        void canonicalize_whenArrayOrderDiffers_shouldReturnDifferentForm() {
            // given
            String first = "{\"tags\":[\"a\",\"b\"]}";
            String second = "{\"tags\":[\"b\",\"a\"]}";

            // when
            String firstResult = canonicalize(first);
            String secondResult = canonicalize(second);

            // then
            assertThat(firstResult).isEqualTo("{\"tags\":[\"a\",\"b\"]}");
            assertThat(firstResult).isNotEqualTo(secondResult);
        }

        @Test
        @DisplayName("UT canonicalize() when a field name contains separators should not collide with two fields")
        void canonicalize_whenFieldNameContainsSeparators_shouldNotCollide() {
            // given
            String twoFields = "{\"a\":1,\"b\":2}";
            String oneWeirdlyNamedField = "{\"a\\\":1,\\\"b\":2}";

            // when
            String twoFieldsResult = canonicalize(twoFields);
            String oneFieldResult = canonicalize(oneWeirdlyNamedField);

            // then
            assertThat(twoFieldsResult).isNotEqualTo(oneFieldResult);
        }

        @Test
        @DisplayName("UT canonicalize() when a string value looks like a number should return a different form")
        void canonicalize_whenStringValueLooksLikeNumber_shouldReturnDifferentForm() {
            // given
            String asString = "{\"a\":\"1\"}";
            String asNumber = "{\"a\":1}";

            // when / then
            assertThat(canonicalize(asString)).isNotEqualTo(canonicalize(asNumber));
        }

        @Test
        @DisplayName("UT canonicalize() when a number is written differently should return a different form")
        void canonicalize_whenNumberWrittenDifferently_shouldReturnDifferentForm() {
            // given
            String withFraction = "{\"a\":1.0}";
            String withoutFraction = "{\"a\":1}";

            // when / then
            assertThat(canonicalize(withFraction)).isNotEqualTo(canonicalize(withoutFraction));
        }

        @Test
        @DisplayName("UT canonicalize() when a value is explicitly null should differ from the field being absent")
        void canonicalize_whenValueIsExplicitlyNull_shouldDifferFromAbsentField() {
            // given
            String explicitNull = "{\"a\":null}";
            String absent = "{}";

            // when
            String explicitNullResult = canonicalize(explicitNull);
            String absentResult = canonicalize(absent);

            // then
            assertThat(explicitNullResult).isEqualTo("{\"a\":null}");
            assertThat(absentResult).isEqualTo("{}");
        }

        @Test
        @DisplayName("UT canonicalize() when a non-ASCII key is used should order by code unit")
        void canonicalize_whenNonAsciiKeyIsUsed_shouldOrderByCodeUnit() {
            // given
            String first = "{\"я\":1,\"a\":2,\"Z\":3}";
            String second = "{\"a\":2,\"Z\":3,\"я\":1}";

            // when
            String firstResult = canonicalize(first);
            String secondResult = canonicalize(second);

            // then
            assertThat(firstResult).isEqualTo("{\"Z\":3,\"a\":2,\"я\":1}");
            assertThat(firstResult).isEqualTo(secondResult);
        }
    }

    @Nested
    @DisplayName("included fields")
    class IncludedFields {

        @Test
        @DisplayName("UT canonicalize() when included fields are set should keep only them")
        void canonicalize_whenIncludedFieldsAreSet_shouldKeepOnlyThem() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .includedFields("a", "b")
                    .build();

            // when
            String result = canonicalize(config, "{\"a\":1,\"b\":2,\"c\":3}");

            // then
            assertThat(result).isEqualTo("\"a\":1,\"b\":2");
        }

        @Test
        @DisplayName("UT canonicalize() when included fields are declared in a different order should return the same form")
        void canonicalize_whenIncludedFieldsDeclaredInDifferentOrder_shouldReturnSameForm() {
            // given
            BodyCanonicalizerConfig straight = completeConfig()
                    .includedFields("a", "b")
                    .build();
            BodyCanonicalizerConfig reversed = completeConfig()
                    .includedFields("b", "a")
                    .build();
            String body = "{\"a\":1,\"b\":2}";

            // when / then
            assertThat(canonicalize(straight, body)).isEqualTo(canonicalize(reversed, body));
        }

        @Test
        @DisplayName("UT canonicalize() when an included field is nested should resolve the dotted path")
        void canonicalize_whenIncludedFieldIsNested_shouldResolveDottedPath() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .includedFields("order.total")
                    .build();

            // when
            String result = canonicalize(config, "{\"order\":{\"total\":42,\"note\":\"x\"}}");

            // then
            assertThat(result).isEqualTo("\"order.total\":42");
        }

        @Test
        @DisplayName("UT canonicalize() when an included field is absent should differ from it being null")
        void canonicalize_whenIncludedFieldIsAbsent_shouldDifferFromNull() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .includedFields("a")
                    .build();

            // when
            String absent = canonicalize(config, "{\"other\":1}");
            String explicitNull = canonicalize(config, "{\"a\":null}");

            // then
            assertThat(absent).isEqualTo("\"a\":" + MISSING_FIELD);
            assertThat(explicitNull).isEqualTo("\"a\":null");
        }

        @Test
        @DisplayName("UT canonicalize() when an included path points inside an array should not resolve")
        void canonicalize_whenIncludedPathPointsInsideArray_shouldNotResolve() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .includedFields("items.sku")
                    .build();

            // when
            String result = canonicalize(config, "{\"items\":[{\"sku\":\"A\"}]}");

            // then
            assertThat(result).isEqualTo("\"items.sku\":" + MISSING_FIELD);
        }
    }

    @Nested
    @DisplayName("excluded fields")
    class ExcludedFields {

        @Test
        @DisplayName("UT canonicalize() when a top level field is excluded should prune it")
        void canonicalize_whenTopLevelFieldIsExcluded_shouldPruneIt() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .excludedFields("traceId")
                    .build();

            // when
            String result = canonicalize(config, "{\"a\":1,\"traceId\":\"x\"}");

            // then
            assertThat(result).isEqualTo("{\"a\":1}");
        }

        @Test
        @DisplayName("UT canonicalize() when a nested field is excluded should prune only that path")
        void canonicalize_whenNestedFieldIsExcluded_shouldPruneOnlyThatPath() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .excludedFields("meta.traceId")
                    .build();

            // when
            String result = canonicalize(config, "{\"traceId\":\"top\",\"meta\":{\"traceId\":\"nested\",\"u\":1}}");

            // then
            assertThat(result).isEqualTo("{\"meta\":{\"u\":1},\"traceId\":\"top\"}");
        }

        @Test
        @DisplayName("UT canonicalize() when an excluded field sits in array elements should prune it from every element")
        void canonicalize_whenExcludedFieldSitsInArrayElements_shouldPruneFromEveryElement() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .excludedFields("items.sku")
                    .build();

            // when
            String result = canonicalize(config, "{\"items\":[{\"qty\":1,\"sku\":\"A\"},{\"qty\":2,\"sku\":\"B\"}]}");

            // then
            assertThat(result).isEqualTo("{\"items\":[{\"qty\":1},{\"qty\":2}]}");
        }

        @Test
        @DisplayName("UT canonicalize() when the excluded field is absent should change nothing")
        void canonicalize_whenExcludedFieldIsAbsent_shouldChangeNothing() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .excludedFields("nope")
                    .build();

            // when / then
            assertThat(canonicalize(config, "{\"a\":1}")).isEqualTo(canonicalize("{\"a\":1}"));
        }

        @Test
        @DisplayName("UT canonicalize() when an excluded field changes value should return the same form")
        void canonicalize_whenExcludedFieldChangesValue_shouldReturnSameForm() {
            // given
            BodyCanonicalizerConfig config = completeConfig()
                    .excludedFields("meta.traceId")
                    .build();

            // when
            String first = canonicalize(config, "{\"a\":1,\"meta\":{\"traceId\":\"one\"}}");
            String second = canonicalize(config, "{\"a\":1,\"meta\":{\"traceId\":\"two\"}}");

            // then
            assertThat(first).isEqualTo(second);
        }
    }

    @Nested
    @DisplayName("excluded fields, exhaustive")
    class ExcludedFieldsExhaustive {

        private String excluding(String body, String... excluded) {
            return canonicalize(
                    completeConfig().excludedFields(excluded).build(),
                    body
            );
        }

        @Test
        @DisplayName("UT canonicalize() when the body is a top level array should still prune inside elements")
        void canonicalize_whenBodyIsTopLevelArray_shouldPruneInsideElements() {
            assertThat(excluding("[{\"a\":1,\"traceId\":\"x\"},{\"a\":2,\"traceId\":\"y\"}]", "traceId"))
                    .isEqualTo("[{\"a\":1},{\"a\":2}]");
        }

        @Test
        @DisplayName("UT canonicalize() when the excluded path is four levels deep should prune it")
        void canonicalize_whenExcludedPathIsFourLevelsDeep_shouldPruneIt() {
            assertThat(excluding("{\"a\":{\"b\":{\"c\":{\"d\":1,\"e\":2}}}}", "a.b.c.d"))
                    .isEqualTo("{\"a\":{\"b\":{\"c\":{\"e\":2}}}}");
        }

        @Test
        @DisplayName("UT canonicalize() when the excluded path is nested in arrays of arrays should prune it")
        void canonicalize_whenExcludedPathIsNestedInArraysOfArrays_shouldPruneIt() {
            assertThat(excluding("{\"a\":[[{\"b\":1,\"c\":2}]]}", "a.b"))
                    .isEqualTo("{\"a\":[[{\"c\":2}]]}");
        }

        @Test
        @DisplayName("UT canonicalize() when an object nests through array, object, array should prune the leaf")
        void canonicalize_whenObjectNestsThroughArrayObjectArray_shouldPruneLeaf() {
            assertThat(excluding("{\"a\":[{\"b\":[{\"c\":1,\"d\":2}]}]}", "a.b.c"))
                    .isEqualTo("{\"a\":[{\"b\":[{\"d\":2}]}]}");
        }

        @Test
        @DisplayName("UT canonicalize() when a whole subtree is excluded should drop it entirely")
        void canonicalize_whenWholeSubtreeIsExcluded_shouldDropItEntirely() {
            assertThat(excluding("{\"a\":1,\"meta\":{\"x\":1,\"y\":[1,2]}}", "meta"))
                    .isEqualTo("{\"a\":1}");
        }

        @Test
        @DisplayName("UT canonicalize() when every field is excluded should return an empty object")
        void canonicalize_whenEveryFieldIsExcluded_shouldReturnEmptyObject() {
            assertThat(excluding("{\"a\":1,\"b\":2}", "a", "b")).isEqualTo("{}");
        }

        @Test
        @DisplayName("UT canonicalize() when only some array elements carry the excluded field should prune where present")
        void canonicalize_whenOnlySomeArrayElementsCarryExcludedField_shouldPruneWherePresent() {
            assertThat(excluding("{\"i\":[{\"q\":1,\"s\":\"A\"},{\"q\":2}]}", "i.s"))
                    .isEqualTo("{\"i\":[{\"q\":1},{\"q\":2}]}");
        }

        @Test
        @DisplayName("UT canonicalize() when several paths are excluded should prune all of them")
        void canonicalize_whenSeveralPathsAreExcluded_shouldPruneAllOfThem() {
            assertThat(excluding("{\"a\":1,\"b\":2,\"m\":{\"t\":3,\"u\":4}}", "b", "m.t"))
                    .isEqualTo("{\"a\":1,\"m\":{\"u\":4}}");
        }

        @Test
        @DisplayName("UT canonicalize() when the excluded name is a prefix of a real field should not prune it")
        void canonicalize_whenExcludedNameIsPrefixOfRealField_shouldNotPruneIt() {
            assertThat(excluding("{\"meta\":1,\"metadata\":2}", "met"))
                    .isEqualTo("{\"meta\":1,\"metadata\":2}");
        }

        @Test
        @DisplayName("UT canonicalize() when the same name sits at several depths should prune only the exact path")
        void canonicalize_whenSameNameSitsAtSeveralDepths_shouldPruneOnlyExactPath() {
            // excluding a bare name does NOT prune nested occurrences of that name
            assertThat(excluding("{\"t\":1,\"m\":{\"t\":2},\"n\":{\"t\":3}}", "t"))
                    .isEqualTo("{\"m\":{\"t\":2},\"n\":{\"t\":3}}");
        }

        @Test
        @DisplayName("UT canonicalize() when a literal dotted name collides with a nested path should prune both")
        void canonicalize_whenLiteralDottedNameCollidesWithNestedPath_shouldPruneBoth() {
            // ambiguity of the dot-separated syntax: "a.b" addresses both the top level field
            // literally named "a.b" and the field "b" inside the object "a"
            assertThat(excluding("{\"a.b\":1,\"a\":{\"b\":2,\"c\":3}}", "a.b"))
                    .isEqualTo("{\"a\":{\"c\":3}}");
        }

        @Test
        @DisplayName("UT canonicalize() when the excluded value is an object should prune the key and its content")
        void canonicalize_whenExcludedValueIsObject_shouldPruneKeyAndContent() {
            assertThat(excluding("{\"a\":{\"deep\":{\"deeper\":1}},\"b\":2}", "a"))
                    .isEqualTo("{\"b\":2}");
        }

        @Test
        @DisplayName("UT canonicalize() when an excluded leaf is added or removed should return the same form")
        void canonicalize_whenExcludedLeafIsAddedOrRemoved_shouldReturnSameForm() {
            assertThat(excluding("{\"a\":1,\"m\":{\"t\":\"x\",\"u\":2}}", "m.t"))
                    .isEqualTo(excluding("{\"a\":1,\"m\":{\"u\":2}}", "m.t"));
        }

        @Test
        @DisplayName("UT canonicalize() when the excluded path has a differently named parent should not prune it")
        void canonicalize_whenExcludedPathHasDifferentlyNamedParent_shouldNotPruneIt() {
            assertThat(excluding("{\"m\":{\"t\":1},\"n\":{\"t\":2}}", "m.t"))
                    .isEqualTo("{\"m\":{},\"n\":{\"t\":2}}");
        }
    }

    @Nested
    @DisplayName("input handling")
    class InputHandling {

        @Test
        @DisplayName("UT canonicalize() when body is not valid JSON should throw IllegalArgumentException")
        void canonicalize_whenBodyIsNotValidJson_shouldThrow() {
            // given
            String truncated = "{\"a\":";

            // when / then
            assertThatThrownBy(() -> canonicalize(truncated))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not valid JSON");
        }

        @Test
        @DisplayName("UT constructor when config is null should throw NullPointerException")
        void constructor_whenConfigIsNull_shouldThrow() {
            assertThatThrownBy(() -> new JsonBodyCanonicalizer(null, objectMapper))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("config cannot be null");
        }

        @Test
        @DisplayName("UT constructor when objectMapper is null should throw NullPointerException")
        void constructor_whenObjectMapperIsNull_shouldThrow() {
            assertThatThrownBy(() -> new JsonBodyCanonicalizer(BodyCanonicalizerConfig.defaults(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("objectMapper cannot be null");
        }
    }
}
