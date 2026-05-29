package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalTerminatedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkWalletRegistrationApprovalTerminatedServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDateTime NOW =
      LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";

  @Mock private LockWalletRegistrationSessionPort lockSessionPort;
  @Mock private SaveWalletRegistrationSessionPort saveSessionPort;

  private MarkWalletRegistrationApprovalTerminatedService service;

  @BeforeEach
  void setUp() {
    service =
        new MarkWalletRegistrationApprovalTerminatedService(
            lockSessionPort, saveSessionPort, CLOCK);
  }

  @Test
  void execute_whenFailedOnchainAndSessionTtlRemains_marksRetryable() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(signedSession()));

    service.execute(command("FAILED_ONCHAIN", "reverted"));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus())
        .isEqualTo(WalletRegistrationStatus.APPROVAL_RETRYABLE);
    assertThat(captor.getValue().getLastErrorCode()).isEqualTo("FAILED_ONCHAIN");
    assertThat(captor.getValue().getLastErrorReason()).isEqualTo("reverted");
  }

  @Test
  void execute_whenFailedOnchainAndSessionTtlElapsed_marksApprovalFailed() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredTtlSignedSession()));

    service.execute(command("FAILED_ONCHAIN", "reverted"));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.APPROVAL_FAILED);
    assertThat(captor.getValue().getLastErrorReason()).isEqualTo("reverted");
  }

  @Test
  void execute_whenReceiptTimeoutAndSessionTtlRemains_marksSponsorNonceBlocked() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(signedSession()));

    service.execute(
        command(WalletRegistrationReceiptTimeout.ERROR_CODE, "receipt timeout from transaction"));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus())
        .isEqualTo(WalletRegistrationStatus.SPONSOR_NONCE_BLOCKED);
    assertThat(captor.getValue().getLastErrorCode())
        .isEqualTo(WalletRegistrationReceiptTimeout.ERROR_CODE);
    assertThat(captor.getValue().getLastErrorReason())
        .isEqualTo(WalletRegistrationReceiptTimeout.ERROR_REASON);
  }

  @Test
  void execute_whenReceiptTimeoutAndSessionTtlElapsed_marksSponsorNonceBlocked() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredTtlSignedSession()));

    service.execute(
        command(WalletRegistrationReceiptTimeout.ERROR_CODE, "receipt timeout from transaction"));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus())
        .isEqualTo(WalletRegistrationStatus.SPONSOR_NONCE_BLOCKED);
    assertThat(captor.getValue().getLastErrorCode())
        .isEqualTo(WalletRegistrationReceiptTimeout.ERROR_CODE);
    assertThat(captor.getValue().getLastErrorReason())
        .isEqualTo(WalletRegistrationReceiptTimeout.ERROR_REASON);
  }

  @Test
  void execute_whenCanceled_marksRetryable() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(signedSession()));

    service.execute(command("CANCELED", "user canceled"));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus())
        .isEqualTo(WalletRegistrationStatus.APPROVAL_RETRYABLE);
  }

  @Test
  void execute_whenExpiredEventArrivesAfterSignature_doesNotMoveBackToRetryable() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(signedSession()));

    service.execute(command("EXPIRED", "execution expired"));

    verify(saveSessionPort, never()).save(any());
  }

  @Test
  void execute_whenExpiredEventArrivesAfterPendingOnchain_doesNotMoveBackToRetryable() {
    WalletRegistrationSession pending =
        signedSession()
            .markApprovalPendingOnchain(
                INTENT_ID, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(pending));

    service.execute(command("EXPIRED", "execution expired"));

    verify(saveSessionPort, never()).save(any());
  }

  @Test
  void execute_whenSessionAlreadyExpired_doesNotOverwriteWithCanceled() {
    WalletRegistrationSession expired =
        approvalRequiredSession()
            .expire(
                MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON,
                "expired",
                NOW.plusSeconds(2));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(expired));

    service.execute(
        command(
            "CANCELED", MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON));

    verify(saveSessionPort, never()).save(any());
  }

  @Test
  void execute_whenSessionAlreadyFinalizationFailed_doesNotDowngrade() {
    WalletRegistrationSession finalizationFailed =
        signedSession()
            .markApprovalPendingOnchain(
                INTENT_ID, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3))
            .markFinalizationFailed("FINALIZATION_FAILED", "db failed", NOW.plusSeconds(4));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(finalizationFailed));

    service.execute(command("CANCELED", "late cancellation"));

    verify(saveSessionPort, never()).save(any());
  }

  @Test
  void execute_whenStaleIntent_noops() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(signedSession()));

    service.execute(
        new MarkWalletRegistrationApprovalTerminatedCommand(
            REGISTRATION_ID, "old", "CANCELED", "old event"));

    verify(saveSessionPort, never()).save(any());
  }

  private static MarkWalletRegistrationApprovalTerminatedCommand command(
      String status, String reason) {
    return new MarkWalletRegistrationApprovalTerminatedCommand(
        REGISTRATION_ID, INTENT_ID, status, reason);
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, 1L, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession signedSession() {
    return approvalRequiredSession()
        .markApprovalSigned(INTENT_ID, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.plusSeconds(2));
  }

  private static WalletRegistrationSession expiredTtlSignedSession() {
    return signedSession().toBuilder().approvalExpiresAt(NOW.minusSeconds(1)).build();
  }
}
