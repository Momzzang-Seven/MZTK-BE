package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ReconcileWalletRegistrationSessionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RunWalletRegistrationRecoveryBatchUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationRecoveryCandidatePort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.springframework.stereotype.Service;

/** Runs one bounded wallet registration recovery reconciliation batch. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunWalletRegistrationRecoveryBatchService
    implements RunWalletRegistrationRecoveryBatchUseCase {

  private final LoadWalletRegistrationRecoveryCandidatePort loadCandidatePort;
  private final ReconcileWalletRegistrationSessionUseCase reconcileUseCase;

  @Override
  public RunWalletRegistrationRecoveryBatchResult execute(
      RunWalletRegistrationRecoveryBatchCommand command) {
    int scanned = 0;
    int recovered = 0;
    int skipped = 0;
    int failed = 0;
    for (WalletRegistrationSession session :
        loadCandidatePort.loadRecoveryCandidates(command.batchSize())) {
      scanned++;
      try {
        ReconcileWalletRegistrationSessionResult result =
            reconcileUseCase.execute(
                new ReconcileWalletRegistrationSessionCommand(session.getPublicId()));
        if (result.recovered()) {
          recovered++;
        } else if (result.skipped()) {
          skipped++;
        }
      } catch (RuntimeException exception) {
        failed++;
        log.warn(
            "Wallet registration recovery failed: registrationId={}",
            session.getPublicId(),
            exception);
      }
    }
    return new RunWalletRegistrationRecoveryBatchResult(scanned, recovered, skipped, failed);
  }
}
