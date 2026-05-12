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
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
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
class MarkWalletRegistrationApprovalSubmittedServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDateTime NOW =
      LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";
  private static final Long USER_ID = 1L;

  @Mock private LockWalletRegistrationSessionPort lockSessionPort;
  @Mock private SaveWalletRegistrationSessionPort saveSessionPort;
  @Mock private LoadWalletApprovalExecutionStatePort loadExecutionStatePort;

  private MarkWalletRegistrationApprovalSubmittedService service;

  @BeforeEach
  void setUp() {
    service =
        new MarkWalletRegistrationApprovalSubmittedService(
            lockSessionPort, saveSessionPort, loadExecutionStatePort, CLOCK);
  }

  @Test
  void execute_whenTxPending_reloadsExecutionStateAndMarksPendingOnchain() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(executionState("PENDING_ONCHAIN", "PENDING")));

    service.execute(command("SIGNED"));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus())
        .isEqualTo(WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN);
    assertThat(captor.getValue().getLatestTransactionId()).isEqualTo(11L);
    assertThat(captor.getValue().getLatestTransactionHash()).isEqualTo("0x" + "c".repeat(64));
  }

  @Test
  void execute_whenTxSignedWithoutReadableState_usesHookStatusAndMarksSigned() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.empty());

    service.execute(command("SIGNED"));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.APPROVAL_SIGNED);
  }

  @Test
  void execute_whenStaleIntent_noopsWithoutReloadingExecutionState() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));

    service.execute(
        new MarkWalletRegistrationApprovalSubmittedCommand(REGISTRATION_ID, "old", "SIGNED"));

    verify(loadExecutionStatePort, never()).loadByExecutionIntentId(any(), any());
    verify(saveSessionPort, never()).save(any());
  }

  private static MarkWalletRegistrationApprovalSubmittedCommand command(String status) {
    return new MarkWalletRegistrationApprovalSubmittedCommand(REGISTRATION_ID, INTENT_ID, status);
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, USER_ID, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletApprovalExecutionStateView executionState(
      String executionStatus, String transactionStatus) {
    return new WalletApprovalExecutionStateView(
        "WALLET_REGISTRATION",
        REGISTRATION_ID,
        "PENDING_EXECUTION",
        "WALLET_ESCROW_APPROVE",
        INTENT_ID,
        executionStatus,
        NOW.plusMinutes(5),
        "EIP7702",
        2,
        null,
        11L,
        transactionStatus,
        "0x" + "c".repeat(64));
  }
}
