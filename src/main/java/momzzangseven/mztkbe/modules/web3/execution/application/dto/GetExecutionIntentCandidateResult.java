package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

public record GetExecutionIntentCandidateResult(
    String executionIntentId,
    ExecutionIntentStatus executionIntentStatus,
    ExecutionResourceType resourceType,
    String resourceId,
    ExecutionActionType actionType,
    Long requesterUserId,
    Long transactionId,
    ExecutionTransactionStatus transactionStatus,
    String txHash,
    String payloadSnapshotJson) {}
