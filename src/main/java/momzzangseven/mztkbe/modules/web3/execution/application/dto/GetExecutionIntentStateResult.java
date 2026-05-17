package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

public record GetExecutionIntentStateResult(
    String executionIntentId,
    ExecutionIntentStatus executionIntentStatus,
    ExecutionActionType actionType,
    Long requesterUserId) {}
