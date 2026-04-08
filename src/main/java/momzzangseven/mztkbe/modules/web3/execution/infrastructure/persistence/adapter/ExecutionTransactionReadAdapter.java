package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ExecutionTransactionReadAdapter implements LoadExecutionTransactionPort {

  private final ManageExecutionTransactionUseCase manageExecutionTransactionUseCase;

  @Override
  public Optional<ExecutionTransactionSummary> findById(Long transactionId) {
    return manageExecutionTransactionUseCase
        .findSummaryById(transactionId)
        .map(
            transaction ->
                new ExecutionTransactionSummary(
                    transaction.transactionId(), transaction.status(), transaction.txHash()));
  }
}
