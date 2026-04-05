package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionTransactionReadAdapter implements LoadExecutionTransactionPort {

  private final Web3TransactionJpaRepository repository;

  @Override
  public Optional<ExecutionTransactionSummary> findById(Long transactionId) {
    return repository
        .findById(transactionId)
        .map(
            entity ->
                new ExecutionTransactionSummary(
                    entity.getId(), entity.getStatus(), entity.getTxHash()));
  }
}
