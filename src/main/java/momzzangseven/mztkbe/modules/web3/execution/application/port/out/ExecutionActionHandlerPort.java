package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

public interface ExecutionActionHandlerPort {

  boolean supports(ExecutionActionType actionType);

  ExecutionActionPlan buildActionPlan(ExecutionIntent intent);

  default void beforeExecute(ExecutionIntent intent, ExecutionActionPlan actionPlan) {}

  default void afterTransactionSubmitted(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionTransactionStatus txStatus) {}

  default void afterExecutionConfirmed(ExecutionIntent intent, ExecutionActionPlan actionPlan) {}

  default void afterExecutionFailedOnchain(
      ExecutionIntent intent, ExecutionActionPlan actionPlan, String failureReason) {}
}
