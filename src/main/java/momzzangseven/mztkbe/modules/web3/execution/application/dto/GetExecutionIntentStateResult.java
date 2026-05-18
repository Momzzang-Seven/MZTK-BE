package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

public record GetExecutionIntentStateResult(
    String executionIntentId,
    ExecutionIntentStatus executionIntentStatus,
    ExecutionActionType actionType,
    Long requesterUserId,
    Long transactionId,
    ExecutionTransactionStatus transactionStatus,
    String txHash) {

  public GetExecutionIntentStateResult(
      String executionIntentId,
      ExecutionIntentStatus executionIntentStatus,
      ExecutionActionType actionType,
      Long requesterUserId) {
    this(executionIntentId, executionIntentStatus, actionType, requesterUserId, null, null, null);
  }
}
