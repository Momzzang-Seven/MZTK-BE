package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.RunInternalExecutionBatchResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

@Slf4j
public class RunInternalExecutionBatchService implements RunInternalExecutionBatchUseCase {

  private final ExecuteInternalExecutionIntentUseCase executeInternalExecutionIntentUseCase;
  private final LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort;

  public RunInternalExecutionBatchService(
      ExecuteInternalExecutionIntentUseCase executeInternalExecutionIntentUseCase,
      LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort) {
    this.executeInternalExecutionIntentUseCase = executeInternalExecutionIntentUseCase;
    this.loadInternalExecutionIssuerPolicyPort = loadInternalExecutionIssuerPolicyPort;
  }

  @Override
  public RunInternalExecutionBatchResult runBatch(Instant now) {
    LoadInternalExecutionIssuerPolicyPort.InternalExecutionIssuerPolicy policy =
        loadInternalExecutionIssuerPolicyPort.loadPolicy();
    int batchSize = policy.batchSize();
    int executedCount = 0;
    int pendingCount = 0;
    int signedCount = 0;
    int quarantinedCount = 0;
    int failedCount = 0;

    for (int i = 0; i < batchSize; i++) {
      try {
        ExecuteInternalExecutionIntentResult result =
            executeInternalExecutionIntentUseCase.execute(
                new ExecuteInternalExecutionIntentCommand(policy.actionTypes()));
        if (!result.executed()) {
          break;
        }
        executedCount++;
        if (result.quarantined()) {
          quarantinedCount++;
          continue;
        }
        if (result.transactionStatus() == ExecutionTransactionStatus.PENDING) {
          pendingCount++;
        } else if (result.transactionStatus() == ExecutionTransactionStatus.SIGNED) {
          signedCount++;
        }
      } catch (RuntimeException e) {
        failedCount++;
        log.error("internal execution issuer batch aborted due to execution failure", e);
        break;
      }
    }

    return new RunInternalExecutionBatchResult(
        executedCount, pendingCount, signedCount, quarantinedCount, failedCount);
  }
}
