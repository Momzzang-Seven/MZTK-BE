package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
class TransactionalCreateExecutionIntentUseCase implements CreateExecutionIntentUseCase {

  private final CreateExecutionIntentUseCase delegate;
  private final TransactionTemplate transactionTemplate;

  @Override
  public CreateExecutionIntentResult execute(CreateExecutionIntentCommand command) {
    return transactionTemplate.execute(status -> delegate.execute(command));
  }
}
