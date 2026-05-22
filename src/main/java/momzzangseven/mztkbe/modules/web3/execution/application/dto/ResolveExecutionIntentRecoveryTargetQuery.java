package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;

/** Query for resolving an execution recovery target by intent, transaction, or resource. */
public record ResolveExecutionIntentRecoveryTargetQuery(
    String executionIntentId,
    Long transactionId,
    ExecutionResourceType resourceType,
    String resourceId) {

  public ResolveExecutionIntentRecoveryTargetQuery {
    if (executionIntentId != null && executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId must not be blank");
    }
    if (transactionId != null && transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (resourceType != null && (resourceId == null || resourceId.isBlank())) {
      throw new Web3InvalidInputException("resourceId is required when resourceType is set");
    }
    if (resourceId != null && !resourceId.isBlank() && resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required when resourceId is set");
    }
  }
}
