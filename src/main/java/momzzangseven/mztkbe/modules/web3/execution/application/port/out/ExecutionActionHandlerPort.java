package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTerminationEvidenceView;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

public interface ExecutionActionHandlerPort {

  boolean supports(ExecutionActionType actionType);

  default boolean supports(ExecutionIntent intent) {
    return supports(intent.getActionType());
  }

  ExecutionActionPlan buildActionPlan(ExecutionIntent intent);

  default void beforeExecute(ExecutionIntent intent, ExecutionActionPlan actionPlan) {}

  default void afterTransactionSubmitted(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionTransactionStatus txStatus) {}

  default void afterExecutionConfirmed(ExecutionIntent intent, ExecutionActionPlan actionPlan) {}

  default void afterExecutionFailedOnchain(
      ExecutionIntent intent, ExecutionActionPlan actionPlan, String failureReason) {}

  default void afterExecutionTerminated(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {}

  default ExecutionTerminationEvidenceView buildTerminationEvidence(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    return ExecutionTerminationEvidenceView.unknown(intent.getPublicId());
  }

  default void afterExecutionTerminated(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason,
      ExecutionTerminationEvidenceView evidence) {
    afterExecutionTerminated(intent, actionPlan, terminalStatus, failureReason);
  }

  static Optional<ExecutionActionHandlerPort> findMatching(
      List<ExecutionActionHandlerPort> handlers, ExecutionIntent intent) {
    if (handlers == null || handlers.isEmpty()) {
      return Optional.empty();
    }

    List<ExecutionActionHandlerPort> actionTypeMatches =
        handlers.stream().filter(handler -> handler.supports(intent.getActionType())).toList();

    List<ExecutionActionHandlerPort> intentMatches =
        actionTypeMatches.stream().filter(handler -> handler.supports(intent)).toList();
    if (intentMatches.size() == 1) {
      return Optional.of(intentMatches.get(0));
    }
    if (intentMatches.isEmpty()) {
      return Optional.empty();
    }

    throw new IllegalStateException(
        "ambiguous execution action handlers for actionType="
            + intent.getActionType()
            + ", resourceType="
            + intent.getResourceType()
            + ", resourceId="
            + intent.getResourceId()
            + ", matches="
            + actionTypeMatches.size()
            + ", intentMatches="
            + intentMatches.size());
  }
}
