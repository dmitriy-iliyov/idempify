package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationLockLostException;
import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawNonBlockingOperation;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guarantees the non-blocking branch takes from its store, judged against a real Postgres: the claim is a
 * lease under a token that the store times by its own clock, every way out of it is conditional on that token,
 * and an expiry exists on a finished record only. That exactly one of many racing calls takes a free key shows
 * only under concurrency, and lives in {@link PostgreSqlNonBlockingOperationRepositoryMultiThreadTest}.
 * <p>
 * The times a row has left are computed at the moment of each read, so a row read twice is compared without
 * them; where a time is the point of the case, it is asserted against the length it was given.
 */
@Testcontainers
class PostgreSqlNonBlockingOperationRepositoryIntegrationTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOCK_TOKEN = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID FOREIGN_LOCK_TOKEN = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final long AN_HOUR = Duration.ofHours(1).toMillis();
    private static final long FIVE_MINUTES = Duration.ofMinutes(5).toMillis();
    private static final long TOLERANCE = Duration.ofMinutes(1).toMillis();
    private static final String[] READ_TIMES = {"remainingMillis", "remainingLockMillis"};

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcClient jdbcClient;
    private PostgreSqlNonBlockingOperationRepository tested;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        try {
            jdbcClient.sql(new ClassPathResource("idempotent_operations_table.sql")
                    .getContentAsString(StandardCharsets.UTF_8)).update();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        tested = new PostgreSqlNonBlockingOperationRepository(jdbcClient);
        jdbcClient.sql("DELETE FROM idempotent_operations").update();
    }

    @Test
    @DisplayName("IT constructor() when jdbcClient is null should throw NullPointerException")
    void constructor_whenJdbcClientIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new PostgreSqlNonBlockingOperationRepository(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("jdbcClient cannot be null");
    }

    @Test
    @DisplayName("IT lockOrSaveAndLock() when the key is free should insert the row as given under a lease and no expiry")
    void lockOrSaveAndLock_whenKeyIsFree_shouldInsertRowAsGivenUnderLeaseAndNoExpiry() {
        // given
        RawNonBlockingOperation toClaim = claim(LOCK_TOKEN);

        // when
        boolean result = tested.lockOrSaveAndLock(toClaim, FIVE_MINUTES);

        // then
        assertThat(result).isTrue();
        RawNonBlockingOperation stored = stored();
        assertThat(stored).usingRecursiveComparison()
                .ignoringFields("createdAt", "remainingMillis", "remainingLockMillis")
                .isEqualTo(toClaim);
        assertThat(stored.remainingMillis()).isNull();
        assertThat(stored.remainingLockMillis()).isBetween(FIVE_MINUTES - TOLERANCE, FIVE_MINUTES);
    }

    @Test
    @DisplayName("IT lockOrSaveAndLock() when another call holds a live claim should refuse and leave the row as it was")
    void lockOrSaveAndLock_whenAnotherCallHoldsLiveClaim_shouldRefuseAndLeaveRowAsItWas() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        RawNonBlockingOperation before = stored();

        // when
        boolean result = tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // then
        assertThat(result).isFalse();
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT lockOrSaveAndLock() when the other call's lease ran out should take the key over and set only the token and lease")
    void lockOrSaveAndLock_whenOtherCallsLeaseRanOut_shouldTakeKeyOverAndSetOnlyTokenAndLease() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        tested.saveResultAndUpdateStatusByLockToken(KEY, "raw-result", OperationStatus.PROCESSED, AN_HOUR,
                FOREIGN_LOCK_TOKEN, OperationStatus.IN_PROCESS);
        expireLease();
        RawNonBlockingOperation before = stored();

        // when
        boolean result = tested.lockOrSaveAndLock(claim(LOCK_TOKEN, "another-fingerprint"), FIVE_MINUTES);

        // then
        assertThat(result).isTrue();
        RawNonBlockingOperation stored = stored();
        assertThat(stored).usingRecursiveComparison()
                .ignoringFields("lockToken", "remainingMillis", "remainingLockMillis")
                .isEqualTo(before);
        assertThat(stored.lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(stored.remainingLockMillis()).isBetween(FIVE_MINUTES - TOLERANCE, FIVE_MINUTES);
        assertThat(stored.remainingMillis()).isBetween(AN_HOUR - TOLERANCE, AN_HOUR);
    }

    @ParameterizedTest
    @EnumSource(value = OperationStatus.class, names = {"IN_PROCESS", "PROCESSED", "EXCEPTIONALLY_PROCESSED"})
    @DisplayName("IT lockOrSaveAndLock() when a row in any status has a lease that ran out should take the key over and leave the row")
    void lockOrSaveAndLock_whenRowInAnyStatusHasLeaseThatRanOut_shouldTakeKeyOverAndLeaveRow(OperationStatus status) {
        // given
        givenAbandonedIn(status);
        RawNonBlockingOperation before = stored();

        // when
        boolean result = tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // then
        assertThat(result).isTrue();
        RawNonBlockingOperation stored = stored();
        assertThat(stored.lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(stored).usingRecursiveComparison()
                .ignoringFields("lockToken", "remainingMillis", "remainingLockMillis")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("IT lockOrSaveAndLock() when the record expired but its lease is live should still refuse")
    void lockOrSaveAndLock_whenRecordExpiredButItsLeaseIsLive_shouldStillRefuse() {
        // given
        givenProcessedUnder(FOREIGN_LOCK_TOKEN);
        expireRecord();
        RawNonBlockingOperation before = stored();

        // when
        boolean result = tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // then
        assertThat(result).isFalse();
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT lockOrSaveAndLock() when the row is held under this token with a lease that ran out should claim it again")
    void lockOrSaveAndLock_whenRowIsHeldUnderThisTokenWithLeaseThatRanOut_shouldClaimItAgain() {
        // given
        tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);
        expireLease();

        // when
        boolean result = tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // then
        assertThat(result).isTrue();
        assertThat(stored().remainingLockMillis()).isBetween(FIVE_MINUTES - TOLERANCE, FIVE_MINUTES);
    }

    @Test
    @DisplayName("IT lockOrSaveAndLock() when the record was removed by its holder should insert the key anew")
    void lockOrSaveAndLock_whenRecordWasRemovedByItsHolder_shouldInsertKeyAnew() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        tested.deleteByLockToken(KEY, FOREIGN_LOCK_TOKEN);

        // when
        boolean result = tested.lockOrSaveAndLock(claim(LOCK_TOKEN, "another-fingerprint"), FIVE_MINUTES);

        // then
        assertThat(result).isTrue();
        assertThat(stored().lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(stored().fingerprint()).isEqualTo("another-fingerprint");
    }

    @Test
    @DisplayName("IT lockOrSaveAndLock() when the claim was let go should take the key at once")
    void lockOrSaveAndLock_whenClaimWasLetGo_shouldTakeKeyAtOnce() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        tested.unlock(KEY, FOREIGN_LOCK_TOKEN);

        // when
        boolean result = tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // then
        assertThat(result).isTrue();
        assertThat(stored().lockToken()).isEqualTo(LOCK_TOKEN);
    }

    @Test
    @DisplayName("IT lockOrSaveAndLock() when the row is already held under this token should claim it again and count the lease anew")
    void lockOrSaveAndLock_whenRowIsAlreadyHeldUnderThisToken_shouldClaimItAgainAndCountLeaseAnew() {
        // given
        tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // when
        boolean result = tested.lockOrSaveAndLock(claim(LOCK_TOKEN), AN_HOUR);

        // then
        assertThat(result).isTrue();
        assertThat(stored().remainingLockMillis()).isBetween(AN_HOUR - TOLERANCE, AN_HOUR);
    }

    @Test
    @DisplayName("IT find() when the key was never claimed should answer an empty Optional")
    void find_whenKeyWasNeverClaimed_shouldAnswerEmptyOptional() {
        assertThat(tested.find(KEY)).isEmpty();
    }

    @Test
    @DisplayName("IT updateByLockToken() when the held record expired should claim it anew clearing what it left and keeping the lease")
    void updateByLockToken_whenHeldRecordExpired_shouldClaimItAnewClearingWhatItLeftAndKeepingLease() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        tested.saveExceptionAndUpdateStatusByLockToken(KEY, "raw-failure", ResultType.ofClass(IllegalStateException.class),
                OperationStatus.EXCEPTIONALLY_PROCESSED, AN_HOUR, FOREIGN_LOCK_TOKEN, OperationStatus.IN_PROCESS);
        expireLease();
        tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);
        jdbcClient.sql("UPDATE idempotent_operations SET response = 'raw-response'").update();
        expireRecord();
        RawNonBlockingOperation before = stored();

        // when
        RawNonBlockingOperation result = tested.updateByLockToken(
                claim(LOCK_TOKEN, "another-fingerprint"),
                OperationStatus.PROCESSED_STATUSES
        );

        // then
        assertThat(result.status()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.fingerprint()).isEqualTo("another-fingerprint");
        assertThat(result.result()).isNull();
        assertThat(result.exceptionClassName()).isNull();
        assertThat(result.response()).isNull();
        assertThat(result.remainingMillis()).isNull();
        assertThat(result.lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(result.remainingLockMillis()).isBetween(FIVE_MINUTES - TOLERANCE, FIVE_MINUTES);
        assertThat(result.createdAt()).isEqualTo(before.createdAt());
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(result);
    }

    @Test
    @DisplayName("IT updateByLockToken() when the row is held under another token should throw OperationLockLostException and leave it")
    void updateByLockToken_whenRowIsHeldUnderAnotherToken_shouldThrowOperationLockLostExceptionAndLeaveIt() {
        // given
        givenProcessedUnder(FOREIGN_LOCK_TOKEN);
        RawNonBlockingOperation before = stored();

        // when / then
        assertThatThrownBy(() -> tested.updateByLockToken(claim(LOCK_TOKEN), OperationStatus.PROCESSED_STATUSES))
                .isInstanceOf(OperationLockLostException.class)
                .hasMessageContaining(KEY.toString());
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT updateByLockToken() when the held row is in none of the statuses should throw OperationLockLostException and leave it")
    void updateByLockToken_whenHeldRowIsInNoneOfStatuses_shouldThrowOperationLockLostExceptionAndLeaveIt() {
        // given
        tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);
        RawNonBlockingOperation before = stored();

        // when / then
        assertThatThrownBy(() -> tested.updateByLockToken(claim(LOCK_TOKEN), OperationStatus.PROCESSED_STATUSES))
                .isInstanceOf(OperationLockLostException.class);
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatusByLockToken() when the row is held in the status should store the result with an expiry from now")
    void saveResultAndUpdateStatusByLockToken_whenRowIsHeldInStatus_shouldStoreResultWithExpiryFromNow() {
        // given
        tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // when
        tested.saveResultAndUpdateStatusByLockToken(KEY, "raw-result", OperationStatus.PROCESSED, AN_HOUR,
                LOCK_TOKEN, OperationStatus.IN_PROCESS);

        // then
        RawNonBlockingOperation stored = stored();
        assertThat(stored.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(stored.result()).isEqualTo("raw-result");
        assertThat(stored.remainingMillis()).isBetween(AN_HOUR - TOLERANCE, AN_HOUR);
        assertThat(stored.lockToken()).isEqualTo(LOCK_TOKEN);
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatusByLockToken() when the result is null should read it back as null")
    void saveResultAndUpdateStatusByLockToken_whenResultIsNull_shouldReadItBackAsNull() {
        // given
        tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // when
        tested.saveResultAndUpdateStatusByLockToken(KEY, null, OperationStatus.PROCESSED, AN_HOUR,
                LOCK_TOKEN, OperationStatus.IN_PROCESS);

        // then
        assertThat(stored().status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(stored().result()).isNull();
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatusByLockToken() when the row is held under another token should throw OperationLockLostException and leave it")
    void saveResultAndUpdateStatusByLockToken_whenRowIsHeldUnderAnotherToken_shouldThrowOperationLockLostExceptionAndLeaveIt() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        RawNonBlockingOperation before = stored();

        // when / then
        assertThatThrownBy(() -> tested.saveResultAndUpdateStatusByLockToken(KEY, "raw-result",
                OperationStatus.PROCESSED, AN_HOUR, LOCK_TOKEN, OperationStatus.IN_PROCESS))
                .isInstanceOf(OperationLockLostException.class)
                .hasMessageContaining(KEY.toString());
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatusByLockToken() when the held row is in another status should throw OperationLockLostException and leave it")
    void saveResultAndUpdateStatusByLockToken_whenHeldRowIsInAnotherStatus_shouldThrowOperationLockLostExceptionAndLeaveIt() {
        // given
        givenProcessedUnder(LOCK_TOKEN);
        RawNonBlockingOperation before = stored();

        // when / then
        assertThatThrownBy(() -> tested.saveResultAndUpdateStatusByLockToken(KEY, "another-result",
                OperationStatus.PROCESSED, AN_HOUR, LOCK_TOKEN, OperationStatus.IN_PROCESS))
                .isInstanceOf(OperationLockLostException.class);
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT saveExceptionAndUpdateStatusByLockToken() when the row is held in the status should keep the failure and its class with an expiry from now")
    void saveExceptionAndUpdateStatusByLockToken_whenRowIsHeldInStatus_shouldKeepFailureAndItsClassWithExpiryFromNow() {
        // given
        tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // when
        tested.saveExceptionAndUpdateStatusByLockToken(KEY, "raw-failure", ResultType.ofClass(IllegalStateException.class),
                OperationStatus.EXCEPTIONALLY_PROCESSED, AN_HOUR, LOCK_TOKEN, OperationStatus.IN_PROCESS);

        // then
        RawNonBlockingOperation stored = stored();
        assertThat(stored.status()).isEqualTo(OperationStatus.EXCEPTIONALLY_PROCESSED);
        assertThat(stored.result()).isEqualTo("raw-failure");
        assertThat(stored.exceptionClassName()).isEqualTo(IllegalStateException.class.getName());
        assertThat(stored.remainingMillis()).isBetween(AN_HOUR - TOLERANCE, AN_HOUR);
    }

    @Test
    @DisplayName("IT saveExceptionAndUpdateStatusByLockToken() when the row is held under another token should throw OperationLockLostException and leave it")
    void saveExceptionAndUpdateStatusByLockToken_whenRowIsHeldUnderAnotherToken_shouldThrowOperationLockLostExceptionAndLeaveIt() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        RawNonBlockingOperation before = stored();

        // when / then
        assertThatThrownBy(() -> tested.saveExceptionAndUpdateStatusByLockToken(KEY, "raw-failure",
                ResultType.ofClass(IllegalStateException.class), OperationStatus.EXCEPTIONALLY_PROCESSED, AN_HOUR,
                LOCK_TOKEN, OperationStatus.IN_PROCESS))
                .isInstanceOf(OperationLockLostException.class)
                .hasMessageContaining(KEY.toString());
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT saveExceptionAndUpdateStatusByLockToken() when the held row is in another status should throw OperationLockLostException and leave it")
    void saveExceptionAndUpdateStatusByLockToken_whenHeldRowIsInAnotherStatus_shouldThrowOperationLockLostExceptionAndLeaveIt() {
        // given
        givenProcessedUnder(LOCK_TOKEN);
        RawNonBlockingOperation before = stored();

        // when / then
        assertThatThrownBy(() -> tested.saveExceptionAndUpdateStatusByLockToken(KEY, "raw-failure",
                ResultType.ofClass(IllegalStateException.class), OperationStatus.EXCEPTIONALLY_PROCESSED, AN_HOUR,
                LOCK_TOKEN, OperationStatus.IN_PROCESS))
                .isInstanceOf(OperationLockLostException.class);
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT deleteByLockToken() when the row is held under the token should remove it and free the key")
    void deleteByLockToken_whenRowIsHeldUnderToken_shouldRemoveItAndFreeKey() {
        // given
        tested.lockOrSaveAndLock(claim(LOCK_TOKEN), FIVE_MINUTES);

        // when
        tested.deleteByLockToken(KEY, LOCK_TOKEN);

        // then
        assertThat(tested.find(KEY)).isEmpty();
    }

    @Test
    @DisplayName("IT deleteByLockToken() when the row is held under another token should throw OperationLockLostException and leave it")
    void deleteByLockToken_whenRowIsHeldUnderAnotherToken_shouldThrowOperationLockLostExceptionAndLeaveIt() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        RawNonBlockingOperation before = stored();

        // when / then
        assertThatThrownBy(() -> tested.deleteByLockToken(KEY, LOCK_TOKEN))
                .isInstanceOf(OperationLockLostException.class)
                .hasMessageContaining(KEY.toString());
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT unlock() when the row is held under the token should clear the claim and leave the record")
    void unlock_whenRowIsHeldUnderToken_shouldClearClaimAndLeaveRecord() {
        // given
        givenProcessedUnder(LOCK_TOKEN);
        RawNonBlockingOperation before = stored();

        // when
        tested.unlock(KEY, LOCK_TOKEN);

        // then
        RawNonBlockingOperation stored = stored();
        assertThat(stored.lockToken()).isNull();
        assertThat(stored.remainingLockMillis()).isNull();
        assertThat(stored).usingRecursiveComparison()
                .ignoringFields("lockToken", "remainingMillis", "remainingLockMillis")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("IT unlock() when the row is held under another token should leave the claim alone without a failure")
    void unlock_whenRowIsHeldUnderAnotherToken_shouldLeaveClaimAloneWithoutFailure() {
        // given
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        RawNonBlockingOperation before = stored();

        // when
        tested.unlock(KEY, LOCK_TOKEN);

        // then
        assertThat(stored()).usingRecursiveComparison().ignoringFields(READ_TIMES).isEqualTo(before);
    }

    @Test
    @DisplayName("IT unlock() when the key was never claimed should do nothing without a failure")
    void unlock_whenKeyWasNeverClaimed_shouldDoNothingWithoutFailure() {
        // when
        tested.unlock(KEY, LOCK_TOKEN);

        // then
        assertThat(tested.find(KEY)).isEmpty();
    }

    private RawNonBlockingOperation stored() {
        return tested.find(KEY).orElseThrow();
    }

    private void givenProcessedUnder(UUID lockToken) {
        tested.lockOrSaveAndLock(claim(lockToken), FIVE_MINUTES);
        tested.saveResultAndUpdateStatusByLockToken(KEY, "raw-result", OperationStatus.PROCESSED, AN_HOUR,
                lockToken, OperationStatus.IN_PROCESS);
    }

    private void givenAbandonedIn(OperationStatus status) {
        tested.lockOrSaveAndLock(claim(FOREIGN_LOCK_TOKEN), FIVE_MINUTES);
        if (OperationStatus.PROCESSED.equals(status)) {
            tested.saveResultAndUpdateStatusByLockToken(KEY, "raw-result", status, AN_HOUR,
                    FOREIGN_LOCK_TOKEN, OperationStatus.IN_PROCESS);
        } else if (OperationStatus.EXCEPTIONALLY_PROCESSED.equals(status)) {
            tested.saveExceptionAndUpdateStatusByLockToken(KEY, "raw-failure",
                    ResultType.ofClass(IllegalStateException.class), status, AN_HOUR, FOREIGN_LOCK_TOKEN,
                    OperationStatus.IN_PROCESS);
        }
        expireLease();
    }

    private static void expireLease() {
        jdbcClient.sql("UPDATE idempotent_operations SET lock_until = clock_timestamp() - INTERVAL '1 second'")
                .update();
    }

    private static void expireRecord() {
        jdbcClient.sql("UPDATE idempotent_operations SET expires_at = clock_timestamp() - INTERVAL '1 second'")
                .update();
    }

    private static RawNonBlockingOperation claim(UUID lockToken) {
        return claim(lockToken, "fingerprint");
    }

    private static RawNonBlockingOperation claim(UUID lockToken, String fingerprint) {
        return new RawNonBlockingOperation(
                KEY,
                OperationStatus.IN_PROCESS,
                false,
                null,
                null,
                null,
                fingerprint,
                null,
                null,
                lockToken,
                null
        );
    }
}
