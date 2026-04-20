package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.transaction;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class ExecutionTransactionReadAdapter implements LoadExecutionTransactionPort {

  private final ManageExecutionTransactionUseCase manageExecutionTransactionUseCase;

  @Override
  public Optional<ExecutionTransactionSummary> findById(Long transactionId) {
    return manageExecutionTransactionUseCase
        .findSummaryById(transactionId)
        .map(
            transaction ->
                new ExecutionTransactionSummary(
                    transaction.transactionId(),
                    ExecutionTransactionStatus.valueOf(transaction.status().name()),
                    transaction.txHash()));
  }

  @Override
  public Map<Long, ExecutionTransactionSummary> findByIds(Collection<Long> transactionIds) {
    if (transactionIds == null || transactionIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, ExecutionTransactionSummary> results = new LinkedHashMap<>();
    manageExecutionTransactionUseCase
        .findSummariesByIds(transactionIds)
        .forEach(
            (transactionId, transaction) ->
                results.put(
                    transactionId,
                    new ExecutionTransactionSummary(
                        transaction.transactionId(),
                        ExecutionTransactionStatus.valueOf(transaction.status().name()),
                        transaction.txHash())));
    return results;
  }
}
