package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ReconcileWalletRegistrationSessionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationRecoveryCandidatePort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunWalletRegistrationRecoveryBatchServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");

  @Mock private LoadWalletRegistrationRecoveryCandidatePort loadCandidatePort;
  @Mock private ReconcileWalletRegistrationSessionUseCase reconcileUseCase;

  private RunWalletRegistrationRecoveryBatchService service;

  @BeforeEach
  void setUp() {
    service = new RunWalletRegistrationRecoveryBatchService(loadCandidatePort, reconcileUseCase);
  }

  @Test
  void execute_countsRecoveredSkippedAndFailedSessions() {
    WalletRegistrationSession session1 = session("registration-1");
    WalletRegistrationSession session2 = session("registration-2");
    WalletRegistrationSession session3 = session("registration-3");
    when(loadCandidatePort.loadRecoveryCandidates(3))
        .thenReturn(List.of(session1, session2, session3));
    when(reconcileUseCase.execute(new ReconcileWalletRegistrationSessionCommand("registration-1")))
        .thenReturn(ReconcileWalletRegistrationSessionResult.recoveredResult());
    when(reconcileUseCase.execute(new ReconcileWalletRegistrationSessionCommand("registration-2")))
        .thenReturn(ReconcileWalletRegistrationSessionResult.skippedResult());
    when(reconcileUseCase.execute(new ReconcileWalletRegistrationSessionCommand("registration-3")))
        .thenThrow(new IllegalStateException("boom"));

    RunWalletRegistrationRecoveryBatchResult result =
        service.execute(new RunWalletRegistrationRecoveryBatchCommand(3));

    assertThat(result.scanned()).isEqualTo(3);
    assertThat(result.recovered()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
  }

  private static WalletRegistrationSession session(String registrationId) {
    return WalletRegistrationSession.create(
            registrationId, 1L, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent("intent-" + registrationId, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }
}
