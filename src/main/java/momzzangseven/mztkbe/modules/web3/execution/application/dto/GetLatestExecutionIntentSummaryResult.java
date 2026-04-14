package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

/**
 * Minimal latest-execution summary for public/internal resume views.
 *
 * <p>This contract intentionally excludes sign request material. Owner-bound polling continues to
 * use {@link GetExecutionIntentResult}.
 */
public record GetLatestExecutionIntentSummaryResult(
    ExecutionResourceType resourceType,
    String resourceId,
    ExecutionResourceStatus resourceStatus,
    String executionIntentId,
    ExecutionIntentStatus executionIntentStatus,
    LocalDateTime expiresAt,
    ExecutionMode mode,
    int signCount,
    Long transactionId,
    ExecutionTransactionStatus transactionStatus,
    String txHash) {

  public GetLatestExecutionIntentSummaryResult {
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
