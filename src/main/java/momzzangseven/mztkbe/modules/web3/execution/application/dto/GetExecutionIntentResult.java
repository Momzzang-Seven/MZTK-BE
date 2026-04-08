package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

/** Read contract for execution intent polling endpoint. */
public record GetExecutionIntentResult(
    ExecutionResourceType resourceType,
    String resourceId,
    ExecutionResourceStatus resourceStatus,
    String executionIntentId,
    ExecutionIntentStatus executionIntentStatus,
    LocalDateTime expiresAt,
    ExecutionMode mode,
    int signCount,
    SignRequestBundle signRequest,
    Long transactionId,
    Web3TxStatus transactionStatus,
    String txHash) {

  /** Validates required fields for read response mapping. */
  public GetExecutionIntentResult {
    if (resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    if (resourceId == null || resourceId.isBlank()) {
      throw new Web3InvalidInputException("resourceId is required");
    }
    if (resourceStatus == null) {
      throw new Web3InvalidInputException("resourceStatus is required");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (executionIntentStatus == null) {
      throw new Web3InvalidInputException("executionIntentStatus is required");
    }
    if (expiresAt == null) {
      throw new Web3InvalidInputException("expiresAt is required");
    }
    if (mode == null) {
      throw new Web3InvalidInputException("mode is required");
    }
    if (signCount <= 0) {
      throw new Web3InvalidInputException("signCount must be positive");
    }
  }
}
