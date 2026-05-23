package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

/** Owner-agnostic execution target used by admin/support recovery wrappers. */
public record ResolveExecutionIntentRecoveryTargetResult(
    String resolutionOutcome,
    String executionIntentId,
    String resourceType,
    String resourceId,
    String actionType,
    String executionIntentStatus,
    Long transactionId,
    ExecutionTransactionStatus transactionStatus,
    String txHash) {}
