package momzzangseven.mztkbe.modules.web3.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.WalletRegistrationApprovalReplayTarget;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.WalletRegistrationRecoveryStateView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.LoadWalletRegistrationRecoveryStatePort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ReplayConfirmedWalletApprovalPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveWalletRegistrationApprovalReplayTargetPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayWalletRegistrationApprovalServiceTest {

  @Mock private ResolveWalletRegistrationApprovalReplayTargetPort resolveTargetPort;
  @Mock private ReplayConfirmedWalletApprovalPort replayConfirmedWalletApprovalPort;
  @Mock private LoadWalletRegistrationRecoveryStatePort loadRecoveryStatePort;

  private ReplayWalletRegistrationApprovalService service;

  @BeforeEach
  void setUp() {
    service =
        new ReplayWalletRegistrationApprovalService(
            resolveTargetPort, replayConfirmedWalletApprovalPort, loadRecoveryStatePort);
  }

  @Test
  void execute_whenTxIdResolvesWalletApprovalAndRegisters_returnsRegistered() {
    when(resolveTargetPort.resolveByTransactionId(24L)).thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(true);
    when(loadRecoveryStatePort.load("registration-1")).thenReturn(Optional.of(state("REGISTERED")));

    var result = service.execute(command(null, 24L, null));

    assertThat(result.outcome()).isEqualTo("REGISTERED");
    assertThat(result.replayInvoked()).isTrue();
    assertThat(result.walletRegistrationStatus()).isEqualTo("REGISTERED");
    verify(replayConfirmedWalletApprovalPort).replay("intent-1", "WALLET_ESCROW_APPROVE");
  }

  @Test
  void execute_whenRegistrationIdOnly_resolvesLatestWalletApprovalTarget() {
    when(resolveTargetPort.resolveByRegistrationId("registration-1"))
        .thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(true);
    when(loadRecoveryStatePort.load("registration-1")).thenReturn(Optional.of(state("REGISTERED")));

    var result = service.execute(command("registration-1", null, null));

    assertThat(result.outcome()).isEqualTo("REGISTERED");
    assertThat(result.registrationId()).isEqualTo("registration-1");
    verify(resolveTargetPort).resolveByRegistrationId("registration-1");
  }

  @Test
  void execute_whenWalletLatestIntentChanged_returnsStaleSuperseded() {
    when(resolveTargetPort.resolveByExecutionIntentId("intent-1"))
        .thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(true);
    when(loadRecoveryStatePort.load("registration-1"))
        .thenReturn(Optional.of(state("APPROVAL_PENDING_ONCHAIN", "intent-2")));

    var result = service.execute(command(null, null, "intent-1"));

    assertThat(result.outcome()).isEqualTo("STALE_SUPERSEDED");
  }

  @Test
  void execute_whenTargetIdentifiersMismatch_returnsTargetMismatch() {
    when(resolveTargetPort.resolveByTransactionId(24L)).thenReturn(Optional.of(target()));

    var result = service.execute(command("other-registration", 24L, null));

    assertThat(result.outcome()).isEqualTo("TARGET_MISMATCH");
    assertThat(result.replayInvoked()).isFalse();
    verifyNoInteractions(replayConfirmedWalletApprovalPort, loadRecoveryStatePort);
  }

  @Test
  void execute_whenTargetNotFound_returnsTargetNotFound() {
    when(resolveTargetPort.resolveByTransactionId(24L)).thenReturn(Optional.empty());

    var result = service.execute(command(null, 24L, null));

    assertThat(result.outcome()).isEqualTo("TARGET_NOT_FOUND");
    assertThat(result.replayInvoked()).isFalse();
    assertThat(result.transactionId()).isEqualTo(24L);
    verifyNoInteractions(replayConfirmedWalletApprovalPort, loadRecoveryStatePort);
  }

  @Test
  void execute_whenReplayInvokedButPostStateMissing_returnsPostStateNotFound() {
    when(resolveTargetPort.resolveByExecutionIntentId("intent-1"))
        .thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(true);
    when(loadRecoveryStatePort.load("registration-1")).thenReturn(Optional.empty());

    var result = service.execute(command(null, null, "intent-1"));

    assertThat(result.outcome()).isEqualTo("POST_STATE_NOT_FOUND");
    assertThat(result.replayInvoked()).isTrue();
  }

  @Test
  void execute_whenReplayPreconditionsFail_returnsNotReplayable() {
    when(resolveTargetPort.resolveByExecutionIntentId("intent-1"))
        .thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(false);
    when(loadRecoveryStatePort.load("registration-1")).thenReturn(Optional.empty());

    var result = service.execute(command(null, null, "intent-1"));

    assertThat(result.outcome()).isEqualTo("NOT_REPLAYABLE");
    assertThat(result.replayInvoked()).isFalse();
  }

  @Test
  void execute_whenFinalizationFailed_returnsFinalizationFailed() {
    when(resolveTargetPort.resolveByExecutionIntentId("intent-1"))
        .thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(true);
    when(loadRecoveryStatePort.load("registration-1"))
        .thenReturn(Optional.of(state("FINALIZATION_FAILED")));

    var result = service.execute(command(null, null, "intent-1"));

    assertThat(result.outcome()).isEqualTo("FINALIZATION_FAILED");
    assertThat(result.walletRegistrationStatus()).isEqualTo("FINALIZATION_FAILED");
  }

  @Test
  void execute_whenLocalConflict_returnsLocalConflict() {
    when(resolveTargetPort.resolveByExecutionIntentId("intent-1"))
        .thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(true);
    when(loadRecoveryStatePort.load("registration-1"))
        .thenReturn(Optional.of(state("LOCAL_CONFLICT")));

    var result = service.execute(command(null, null, "intent-1"));

    assertThat(result.outcome()).isEqualTo("LOCAL_CONFLICT");
    assertThat(result.walletRegistrationStatus()).isEqualTo("LOCAL_CONFLICT");
  }

  @Test
  void execute_whenReplayDidNotReachTerminalState_returnsReplayedNoTerminalChange() {
    when(resolveTargetPort.resolveByExecutionIntentId("intent-1"))
        .thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(true);
    when(loadRecoveryStatePort.load("registration-1"))
        .thenReturn(Optional.of(state("APPROVAL_PENDING_ONCHAIN")));

    var result = service.execute(command(null, null, "intent-1"));

    assertThat(result.outcome()).isEqualTo("REPLAYED_NO_TERMINAL_CHANGE");
    assertThat(result.walletRegistrationStatus()).isEqualTo("APPROVAL_PENDING_ONCHAIN");
  }

  @Test
  void execute_whenNewerWalletRegistrationExists_returnsNewerAttemptExists() {
    when(resolveTargetPort.resolveByExecutionIntentId("intent-1"))
        .thenReturn(Optional.of(target()));
    when(replayConfirmedWalletApprovalPort.replay("intent-1", "WALLET_ESCROW_APPROVE"))
        .thenReturn(true);
    when(loadRecoveryStatePort.load("registration-1"))
        .thenReturn(Optional.of(state("APPROVAL_PENDING_ONCHAIN", "intent-1", true)));

    var result = service.execute(command(null, null, "intent-1"));

    assertThat(result.outcome()).isEqualTo("NEWER_ATTEMPT_EXISTS");
    assertThat(result.newerWalletRegistrationExists()).isTrue();
  }

  @Test
  void execute_whenTargetIsNotWalletApproval_doesNotReplay() {
    WalletRegistrationApprovalReplayTarget notWallet =
        new WalletRegistrationApprovalReplayTarget(
            "intent-1",
            "QUESTION",
            "10",
            "QNA_QUESTION_CREATE",
            "CONFIRMED",
            24L,
            "SUCCEEDED",
            "0x" + "a".repeat(64));
    when(resolveTargetPort.resolveByExecutionIntentId("intent-1"))
        .thenReturn(Optional.of(notWallet));

    var result = service.execute(command(null, null, "intent-1"));

    assertThat(result.outcome()).isEqualTo("NOT_WALLET_APPROVAL_TARGET");
    verifyNoInteractions(replayConfirmedWalletApprovalPort, loadRecoveryStatePort);
  }

  @Test
  void execute_whenAuditEvidenceTooLarge_rejectsBeforeResolvingTarget() {
    ReplayWalletRegistrationApprovalCommand command =
        new ReplayWalletRegistrationApprovalCommand(
            9L, null, 24L, null, "manual replay", "x".repeat(1001));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("evidence must not exceed 1000");

    verifyNoInteractions(
        resolveTargetPort, replayConfirmedWalletApprovalPort, loadRecoveryStatePort);
  }

  private ReplayWalletRegistrationApprovalCommand command(
      String registrationId, Long transactionId, String executionIntentId) {
    return new ReplayWalletRegistrationApprovalCommand(
        9L, registrationId, transactionId, executionIntentId, "manual replay", "support-1");
  }

  private WalletRegistrationApprovalReplayTarget target() {
    return new WalletRegistrationApprovalReplayTarget(
        "intent-1",
        "WALLET_REGISTRATION",
        "registration-1",
        "WALLET_ESCROW_APPROVE",
        "PENDING_ONCHAIN",
        24L,
        "SUCCEEDED",
        "0x" + "a".repeat(64));
  }

  private WalletRegistrationRecoveryStateView state(String status) {
    return state(status, "intent-1");
  }

  private WalletRegistrationRecoveryStateView state(String status, String latestExecutionIntentId) {
    return state(status, latestExecutionIntentId, false);
  }

  private WalletRegistrationRecoveryStateView state(
      String status, String latestExecutionIntentId, boolean newerWalletRegistrationExists) {
    return new WalletRegistrationRecoveryStateView(
        "registration-1",
        7L,
        "0x" + "b".repeat(40),
        status,
        latestExecutionIntentId,
        24L,
        "0x" + "a".repeat(64),
        null,
        null,
        newerWalletRegistrationExists,
        "REGISTERED".equals(status) ? 77L : null);
  }
}
