package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.IdempotencyEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultFingerprintMatcherUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    IdempotencyEventListener eventListener;

    @Mock
    FingerprintPolicy policy;

    @InjectMocks
    DefaultFingerprintMatcher tested;

    @Test
    @DisplayName("UT constructor when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultFingerprintMatcher(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT match() should ask the policy to compare the stored fingerprint against the current one")
    void match_shouldAskPolicyToCompareStoredFingerprintAgainstCurrentOne() {
        // given
        when(policy.match("stored", "current")).thenReturn(true);

        // when
        tested.match("current", "stored", policy, KEY);

        // then
        verify(policy, times(1)).match("stored", "current");
    }

    @Test
    @DisplayName("UT match() when the fingerprints agree should leave the mismatch path untouched")
    void match_whenFingerprintsAgree_shouldLeaveMismatchPathUntouched() {
        // given
        when(policy.match("stored", "current")).thenReturn(true);

        // when
        tested.match("current", "stored", policy, KEY);

        // then
        verifyNoInteractions(eventListener);
        verify(policy, never()).handle(any());
    }

    @Test
    @DisplayName("UT match() when the fingerprints disagree should report the mismatch and hand it to the policy")
    void match_whenFingerprintsDisagree_shouldReportMismatchAndHandItToPolicy() {
        // given
        when(policy.match("stored", "current")).thenReturn(false);

        // when
        tested.match("current", "stored", policy, KEY);

        // then
        verify(eventListener, times(1)).onFingerprintMismatch();

        ArgumentCaptor<FingerprintMismatchContext> captor = ArgumentCaptor.forClass(FingerprintMismatchContext.class);
        verify(policy, times(1)).handle(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(KEY);
        assertThat(captor.getValue().getPreviousFingerprint()).isEqualTo("stored");
        assertThat(captor.getValue().getCurrentFingerprint()).isEqualTo("current");
    }
}
