package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

/** Runs non-authoritative action hooks without letting hook failures roll back execution writes. */
@Slf4j
final class ExecutionActionHookRunner {

  private ExecutionActionHookRunner() {}

  static void afterTransactionSubmitted(
      RunAfterCommitPort runAfterCommitPort,
      ExecutionActionHandlerPort actionHandler,
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionTransactionStatus txStatus) {
    runAfterCommitPort.runAfterCommit(
        () -> runAfterTransactionSubmitted(actionHandler, intent, actionPlan, txStatus));
  }

  private static void runAfterTransactionSubmitted(
      ExecutionActionHandlerPort actionHandler,
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionTransactionStatus txStatus) {
    try {
      actionHandler.afterTransactionSubmitted(intent, actionPlan, txStatus);
    } catch (RuntimeException exception) {
      log.error(
          "failed to run execution action hook after transaction submission: executionIntentId={},"
              + " txStatus={}",
          intent.getPublicId(),
          txStatus,
          exception);
    }
  }
}
