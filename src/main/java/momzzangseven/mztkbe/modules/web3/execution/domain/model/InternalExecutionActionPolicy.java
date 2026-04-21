package momzzangseven.mztkbe.modules.web3.execution.domain.model;

import java.util.List;

public enum InternalExecutionActionPolicy {
  QNA_ADMIN(List.of(ExecutionActionType.QNA_ADMIN_SETTLE, ExecutionActionType.QNA_ADMIN_REFUND)),
  MARKETPLACE_ADMIN(
      List.of(
          ExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
          ExecutionActionType.MARKETPLACE_ADMIN_REFUND)),
  QNA_AND_MARKETPLACE_ADMIN(
      List.of(
          ExecutionActionType.QNA_ADMIN_SETTLE,
          ExecutionActionType.QNA_ADMIN_REFUND,
          ExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
          ExecutionActionType.MARKETPLACE_ADMIN_REFUND));

  private final List<ExecutionActionType> actionTypes;

  InternalExecutionActionPolicy(List<ExecutionActionType> actionTypes) {
    this.actionTypes = List.copyOf(actionTypes);
  }

  public List<ExecutionActionType> actionTypes() {
    return actionTypes;
  }
}
