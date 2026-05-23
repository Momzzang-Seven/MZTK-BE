package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SyncWalletApprovalExecutionSuccessPort;
import org.springframework.stereotype.Component;

/** Wallet-owned adapter for replaying execution success synchronization. */
@Component
@RequiredArgsConstructor
public class WalletApprovalExecutionSuccessSyncAdapter
    implements SyncWalletApprovalExecutionSuccessPort {

  private final Optional<MarkExecutionIntentSucceededUseCase> markSucceededUseCase;

  @Override
  public void syncSucceededTransaction(Long transactionId) {
    markSucceededUseCase
        .orElseThrow(
            () -> new WalletApprovalUnavailableException("execution success sync is unavailable"))
        .execute(transactionId);
  }
}
