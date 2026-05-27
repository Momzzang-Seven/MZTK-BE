package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentPendingOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.MarkExecutionIntentPendingOnchainPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarkExecutionIntentPendingOnchainAdapter
    implements MarkExecutionIntentPendingOnchainPort {

  private final ObjectProvider<MarkExecutionIntentPendingOnchainUseCase> useCaseProvider;

  @Override
  public void markPendingOnchain(Long transactionId) {
    MarkExecutionIntentPendingOnchainUseCase useCase = useCaseProvider.getIfAvailable();
    if (useCase != null) {
      useCase.execute(transactionId);
    }
  }
}
