package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;

public interface LoadExecutionTransactionPort {

  Optional<ExecutionTransactionSummary> findById(Long transactionId);

  default Map<Long, ExecutionTransactionSummary> findByIds(Collection<Long> transactionIds) {
    Map<Long, ExecutionTransactionSummary> results = new LinkedHashMap<>();
    for (Long transactionId : transactionIds) {
      findById(transactionId).ifPresent(summary -> results.put(transactionId, summary));
    }
    return results;
  }
}
