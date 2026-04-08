package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;

public interface LoadExecutionTransactionPort {

  Optional<ExecutionTransactionSummary> findById(Long transactionId);
}
