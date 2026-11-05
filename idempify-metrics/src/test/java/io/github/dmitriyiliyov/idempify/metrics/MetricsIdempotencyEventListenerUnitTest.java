package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.FailureType;
import io.github.dmitriyiliyov.idempify.core.IdempotencyEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsIdempotencyEventListenerUnitTest {

    private static final String METER = "idempify.operations";
    private static final String TAG = "outcome";
    private static final String FAILURE_TAG = "failure";
    private static final String NO_FAILURE = "none";
    private static final String FAILURE = "failure";
    private static final String DUPLICATE = "duplicate";
    private static final String CONFLICT = "conflict";
    private static final String MISMATCH = "fingerprint-mismatch";
    private static final String BUSINESS_FAILURE = "business-failure";
    private static final String SUCCESS = "success";
    private static final String EXCEPTIONALLY_PROCESSED = "exceptionally-processed";

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("UT constructor() when registry is null should throw NullPointerException")
    void constructor_whenRegistryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new MetricsIdempotencyEventListener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("registry cannot be null");
    }

    @Test
    @DisplayName("UT constructor() should register every outcome before the first one arrives")
    void constructor_shouldRegisterEveryOutcomeBeforeFirstOneArrives() {
        // when
        new MetricsIdempotencyEventListener(registry);

        // then
        assertThat(registry.get(METER).counters()).hasSize(6 + FailureType.values().length);
        for (String outcome : outcomes()) {
            assertThat(count(outcome)).as(outcome).isZero();
        }
        for (FailureType type : FailureType.values()) {
            assertThat(failureCount(type)).as(type.name()).isZero();
        }
    }

    @Test
    @DisplayName("UT constructor() should keep every outcome in one meter named under the library")
    void constructor_shouldKeepEveryOutcomeInOneMeterNamedUnderLibrary() {
        // when
        new MetricsIdempotencyEventListener(registry);

        // then
        assertThat(METER).startsWith("idempify.");
        assertThat(METER).doesNotContain("count");
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getName()).isEqualTo(METER));
        assertThat(registry.get(METER).counter().getId().getDescription()).isNotBlank();
    }

    @Test
    @DisplayName("UT onDuplicate() should count the duplicate and nothing else")
    void onDuplicate_shouldCountDuplicateAndNothingElse() {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onDuplicate();

        // then
        assertThat(count(DUPLICATE)).isEqualTo(1.0);
        assertThatEveryOtherOutcomeStayedAtZero(DUPLICATE);
    }

    @Test
    @DisplayName("UT onConflict() should count the conflict and nothing else")
    void onConflict_shouldCountConflictAndNothingElse() {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onConflict();

        // then
        assertThat(count(CONFLICT)).isEqualTo(1.0);
        assertThatEveryOtherOutcomeStayedAtZero(CONFLICT);
    }

    @Test
    @DisplayName("UT onFingerprintMismatch() should count the mismatch and nothing else")
    void onFingerprintMismatch_shouldCountMismatchAndNothingElse() {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onFingerprintMismatch();

        // then
        assertThat(count(MISMATCH)).isEqualTo(1.0);
        assertThatEveryOtherOutcomeStayedAtZero(MISMATCH);
    }

    @Test
    @DisplayName("UT onBusinessFailure() should count the business failure and nothing else")
    void onBusinessFailure_shouldCountBusinessFailureAndNothingElse() {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onBusinessFailure();

        // then
        assertThat(count(BUSINESS_FAILURE)).isEqualTo(1.0);
        assertThatEveryOtherOutcomeStayedAtZero(BUSINESS_FAILURE);
    }

    @ParameterizedTest
    @EnumSource(FailureType.class)
    @DisplayName("UT onFailure() should count the failure under its type and nothing else")
    void onFailure_shouldCountFailureUnderItsTypeAndNothingElse(FailureType type) {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onFailure(type);

        // then
        assertThat(failureCount(type)).isEqualTo(1.0);
        assertThat(registry.get(METER).counters().stream().mapToDouble(Counter::count).sum()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("UT constructor() should give every series the same tag keys")
    void constructor_shouldGiveEverySeriesSameTagKeys() {
        // when
        new MetricsIdempotencyEventListener(registry);

        // then
        assertThat(registry.get(METER).counters()).allSatisfy(counter -> {
            assertThat(counter.getId().getTag(TAG)).isNotNull();
            assertThat(counter.getId().getTag(FAILURE_TAG)).isNotNull();
        });
    }

    @Test
    @DisplayName("UT onSuccess() should count the success and nothing else")
    void onSuccess_shouldCountSuccessAndNothingElse() {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onSuccess();

        // then
        assertThat(count(SUCCESS)).isEqualTo(1.0);
        assertThatEveryOtherOutcomeStayedAtZero(SUCCESS);
    }

    @Test
    @DisplayName("UT onExceptionallySuccess() should count the failure kept on the record and nothing else")
    void onExceptionallySuccess_shouldCountFailureKeptOnBusinessRecordAndNothingElse() {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onExceptionallySuccess();

        // then
        assertThat(count(EXCEPTIONALLY_PROCESSED)).isEqualTo(1.0);
        assertThatEveryOtherOutcomeStayedAtZero(EXCEPTIONALLY_PROCESSED);
    }

    @Test
    @DisplayName("UT onSuccess() when the same outcome comes back should add up rather than replace")
    void onSuccess_whenSameOutcomeComesBack_shouldAddUpRatherThanReplace() {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onSuccess();
        tested.onSuccess();
        tested.onSuccess();

        // then
        assertThat(count(SUCCESS)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("UT increments() when outcomes differ should add up across the meter as well as within it")
    void increments_whenOutcomesDiffer_shouldAddUpAcrossMeterAsWellAsWithinIt() {
        // given
        IdempotencyEventListener tested = new MetricsIdempotencyEventListener(registry);

        // when
        tested.onSuccess();
        tested.onSuccess();
        tested.onDuplicate();

        // then
        assertThat(registry.get(METER).counters().stream().mapToDouble(Counter::count).sum())
                .isEqualTo(3.0);
    }

    @Test
    @DisplayName("UT constructor() when a second listener shares the registry should reuse the series already there")
    void constructor_whenSecondListenerSharesRegistry_shouldReuseSeriesAlreadyThere() {
        // given
        IdempotencyEventListener first = new MetricsIdempotencyEventListener(registry);
        IdempotencyEventListener second = new MetricsIdempotencyEventListener(registry);

        // when
        first.onSuccess();
        second.onSuccess();

        // then
        assertThat(registry.get(METER).counters()).hasSize(6 + FailureType.values().length);
        assertThat(count(SUCCESS)).isEqualTo(2.0);
    }

    private void assertThatEveryOtherOutcomeStayedAtZero(String counted) {
        for (String outcome : outcomes()) {
            if (!outcome.equals(counted)) {
                assertThat(count(outcome)).as(outcome).isZero();
            }
        }
    }

    private static String[] outcomes() {
        return new String[]{DUPLICATE, CONFLICT, MISMATCH, BUSINESS_FAILURE, SUCCESS, EXCEPTIONALLY_PROCESSED};
    }

    private double count(String outcome) {
        return registry.get(METER).tag(TAG, outcome).tag(FAILURE_TAG, NO_FAILURE).counter().count();
    }

    private double failureCount(FailureType type) {
        return registry.get(METER)
                .tag(TAG, FAILURE)
                .tag(FAILURE_TAG, type.name().toLowerCase().replace('_', '-'))
                .counter()
                .count();
    }
}
