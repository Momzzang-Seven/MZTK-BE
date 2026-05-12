package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CancelExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import org.springframework.stereotype.Component;

/** Wallet-owned adapter for canceling still-signable approval execution intents. */
@Component
@RequiredArgsConstructor
public class WalletApprovalExecutionCancelAdapter implements CancelWalletApprovalExecutionPort {

  private final Optional<CancelExecutionIntentUseCase> cancelExecutionIntentUseCase;

  @Override
  public boolean cancelIfSignable(String executionIntentId, String errorCode, String errorReason) {
    return cancelExecutionIntentUseCase
        .orElseThrow(
            () -> new WalletApprovalUnavailableException("execution cancel is unavailable"))
        .cancelIfSignable(
            new CancelExecutionIntentCommand(executionIntentId, errorCode, errorReason));
  }
}
