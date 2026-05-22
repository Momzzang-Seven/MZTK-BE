package momzzangseven.mztkbe.modules.web3.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
        "REGISTERED".equals(status) ? 77L : null);
  }
}
