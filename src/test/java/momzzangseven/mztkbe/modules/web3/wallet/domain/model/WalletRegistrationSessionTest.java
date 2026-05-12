package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WalletRegistrationSessionTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final String PUBLIC_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String WALLET_ADDRESS = "0x" + "a".repeat(40);
  private static final String CHALLENGE_NONCE = "challenge-nonce";
  private static final String EXECUTION_INTENT_ID = "approval-intent-1";

  @Test
  void validApprovalFlow_movesToRegistered() {
    WalletRegistrationSession session =
        newSession()
            .attachApprovalIntent(EXECUTION_INTENT_ID, NOW.plusMinutes(5), NOW.plusSeconds(1))
            .markApprovalSigned(
                EXECUTION_INTENT_ID, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.plusSeconds(2))
            .markApprovalPendingOnchain(
                EXECUTION_INTENT_ID,
                10L,
                "0x" + "b".repeat(64),
                "PENDING_ONCHAIN",
                NOW.plusSeconds(3))
            .markRegistered(77L, NOW.plusSeconds(4));

    assertThat(session.getStatus()).isEqualTo(WalletRegistrationStatus.REGISTERED);
    assertThat(session.getRegisteredWalletId()).isEqualTo(77L);
    assertThat(session.isTerminal()).isTrue();
  }

  @Test
  void terminalTransitions_recordErrorsAndPreventFurtherMutation() {
    WalletRegistrationSession failed =
        newSession().markApprovalFailed("EXECUTION_FAILED", "onchain failure", NOW.plusSeconds(1));
    WalletRegistrationSession expired =
        newSession().expire("SESSION_EXPIRED", "approval session expired", NOW.plusSeconds(1));
    WalletRegistrationSession canceled =
        newSession().cancel("USER_CANCELED", "approval canceled", NOW.plusSeconds(1));

    assertThat(failed.getStatus()).isEqualTo(WalletRegistrationStatus.APPROVAL_FAILED);
    assertThat(expired.getStatus()).isEqualTo(WalletRegistrationStatus.EXPIRED);
    assertThat(canceled.getStatus()).isEqualTo(WalletRegistrationStatus.CANCELED);
    assertThat(failed.isTerminal()).isTrue();
    assertThat(expired.isTerminal()).isTrue();
    assertThat(canceled.isTerminal()).isTrue();
    assertThatThrownBy(() -> expired.markApprovalFailed("X", "Y", NOW.plusSeconds(2)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void postConfirmLocalFailures_areRecoverableWithoutNewApprovalIntent() {
    WalletRegistrationSession pending =
        signedSession()
            .markApprovalPendingOnchain(
                EXECUTION_INTENT_ID,
                10L,
                "0x" + "b".repeat(64),
                "PENDING_ONCHAIN",
                NOW.plusSeconds(3));

    WalletRegistrationSession finalizationFailed =
        pending.markFinalizationFailed(
            "FINALIZATION_FAILED", "local save failed", NOW.plusSeconds(4));
    WalletRegistrationSession localConflict =
        pending.markLocalConflict("LOCAL_CONFLICT", "active wallet conflict", NOW.plusSeconds(4));

    assertThat(finalizationFailed.getStatus())
        .isEqualTo(WalletRegistrationStatus.FINALIZATION_FAILED);
    assertThat(localConflict.getStatus()).isEqualTo(WalletRegistrationStatus.LOCAL_CONFLICT);
    assertThat(finalizationFailed.canCreateApprovalIntent()).isFalse();
    assertThat(localConflict.canCreateApprovalIntent()).isFalse();
    assertThat(finalizationFailed.markRegistered(77L, NOW.plusSeconds(5)).getStatus())
        .isEqualTo(WalletRegistrationStatus.REGISTERED);
  }

  @Test
  void markApprovalConfirmed_normalizesRequiredSessionForFinalization() {
    WalletRegistrationSession confirmed =
        newSession()
            .attachApprovalIntent(EXECUTION_INTENT_ID, NOW.plusMinutes(5), NOW.plusSeconds(1))
            .markApprovalConfirmed(
                EXECUTION_INTENT_ID, 10L, "0x" + "b".repeat(64), "CONFIRMED", NOW.plusSeconds(2));

    assertThat(confirmed.getStatus()).isEqualTo(WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN);
    assertThat(confirmed.getConfirmedAt()).isEqualTo(NOW.plusSeconds(2));
    assertThat(confirmed.getLatestTransactionId()).isEqualTo(10L);
    assertThat(confirmed.markRegistered(77L, NOW.plusSeconds(3)).getStatus())
        .isEqualTo(WalletRegistrationStatus.REGISTERED);
  }

  @Test
  void confirmedOrSubmittedSessions_cannotCreateAnotherApprovalIntent() {
    WalletRegistrationSession signed = signedSession();
    WalletRegistrationSession pending =
        signed.markApprovalPendingOnchain(
            EXECUTION_INTENT_ID, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3));

    assertThat(signed.canCreateApprovalIntent()).isFalse();
    assertThat(pending.canCreateApprovalIntent()).isFalse();
    assertThatThrownBy(
            () ->
                signed.attachApprovalIntent(
                    "another-intent", NOW.plusMinutes(5), NOW.plusSeconds(4)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                pending.attachApprovalIntent(
                    "another-intent", NOW.plusMinutes(5), NOW.plusSeconds(4)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void ttlExpiry_isLimitedToPreSubmissionStatuses() {
    WalletRegistrationSession retryable =
        newSession()
            .attachApprovalIntent(EXECUTION_INTENT_ID, NOW.plusMinutes(5), NOW.plusSeconds(1))
            .markApprovalRetryable("CANCELED", "wallet canceled", NOW.plusSeconds(2));

    assertThat(newSession().expire("EXPIRED", "expired", NOW.plusSeconds(1)).getStatus())
        .isEqualTo(WalletRegistrationStatus.EXPIRED);
    assertThat(retryable.expire("EXPIRED", "expired", NOW.plusSeconds(3)).getStatus())
        .isEqualTo(WalletRegistrationStatus.EXPIRED);
    assertThatThrownBy(() -> signedSession().expire("EXPIRED", "expired", NOW.plusSeconds(3)))
        .isInstanceOf(IllegalStateException.class);
  }

  private static WalletRegistrationSession newSession() {
    return WalletRegistrationSession.create(
        PUBLIC_ID, 1L, WALLET_ADDRESS, CHALLENGE_NONCE, NOW.plusMinutes(30), NOW);
  }

  private static WalletRegistrationSession signedSession() {
    return newSession()
        .attachApprovalIntent(EXECUTION_INTENT_ID, NOW.plusMinutes(5), NOW.plusSeconds(1))
        .markApprovalSigned(
            EXECUTION_INTENT_ID, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.plusSeconds(2));
  }
}
