package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

/** Owner-agnostic execution target used by admin/support recovery wrappers. */
public record ResolveExecutionIntentRecoveryTargetResult(
    String executionIntentId,
    ExecutionResourceType resourceType,
    String resourceId,
    ExecutionActionType actionType,
    ExecutionIntentStatus executionIntentStatus,
    Long transactionId,
    ExecutionTransactionStatus transactionStatus,
    String txHash) {}
