package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.junit.jupiter.api.Test;

class WalletRegistrationStatusResultTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";

  @Test
  void from_whenPendingOnchainTransactionUnconfirmed_keepsPendingUntilSessionIsMarkedBlocked() {
    WalletRegistrationStatusResult result =
        WalletRegistrationStatusResult.from(
            pendingOnchainSession(NOW.plusMinutes(30)),
            state("PENDING_ONCHAIN", "UNCONFIRMED", 10L),
            NOW);

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN);
    assertThat(result.nextAction())
        .isEqualTo(WalletRegistrationNextAction.WAIT_FOR_APPROVAL_TRANSACTION);
    assertThat(result.lastErrorCode()).isNull();
    assertThat(result.lastErrorReason()).isNull();
    assertThat(result.transaction().transactionStatus()).isEqualTo("UNCONFIRMED");
  }

  @Test
  void from_whenPendingOnchainTransactionUnconfirmedAndTtlElapsed_keepsPendingOnchain() {
    WalletRegistrationStatusResult result =
        WalletRegistrationStatusResult.from(
            pendingOnchainSession(NOW.minusSeconds(1)),
            state("PENDING_ONCHAIN", "UNCONFIRMED", 10L),
            NOW);

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN);
    assertThat(result.nextAction())
        .isEqualTo(WalletRegistrationNextAction.WAIT_FOR_APPROVAL_TRANSACTION);
    assertThat(result.lastErrorCode()).isNull();
  }

  @Test
  void from_whenPendingTransactionStillPending_keepsWaitAction() {
    WalletRegistrationStatusResult result =
        WalletRegistrationStatusResult.from(
            pendingOnchainSession(NOW.plusMinutes(30)),
            state("PENDING_ONCHAIN", "PENDING", 10L),
            NOW);

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN);
    assertThat(result.nextAction())
        .isEqualTo(WalletRegistrationNextAction.WAIT_FOR_APPROVAL_TRANSACTION);
    assertThat(result.lastErrorCode()).isNull();
  }

  private static WalletRegistrationSession pendingOnchainSession(LocalDateTime approvalExpiresAt) {
    return WalletRegistrationSession.create(
            REGISTRATION_ID,
            1L,
            "0x" + "a".repeat(40),
            "nonce-1",
            approvalExpiresAt,
            NOW.minusMinutes(31))
        .attachApprovalIntent(INTENT_ID, approvalExpiresAt, NOW.minusMinutes(30))
        .markApprovalSigned(INTENT_ID, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.minusSeconds(2))
        .markApprovalPendingOnchain(
            INTENT_ID, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.minusSeconds(1));
  }

  private static WalletApprovalExecutionStateView state(
      String executionStatus, String transactionStatus, Long transactionId) {
    return new WalletApprovalExecutionStateView(
        "WALLET_REGISTRATION",
        REGISTRATION_ID,
        "PENDING_EXECUTION",
        "WALLET_ESCROW_APPROVE",
        INTENT_ID,
        executionStatus,
        NOW.plusMinutes(5),
        1L,
        "EIP7702",
        2,
        null,
        null,
        transactionId,
        transactionStatus,
        transactionId == null ? null : "0x" + "c".repeat(64));
  }
}
