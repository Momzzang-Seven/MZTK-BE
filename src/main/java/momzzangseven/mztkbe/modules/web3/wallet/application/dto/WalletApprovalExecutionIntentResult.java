package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record WalletApprovalExecutionIntentResult(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    WalletApprovalSignRequestBundle signRequest,
    boolean existing) {

  public WalletApprovalExecutionIntentResult {
    if (resource == null) {
      throw new Web3InvalidInputException("resource is required");
    }
    if (actionType == null || actionType.isBlank()) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (executionIntent == null) {
      throw new Web3InvalidInputException("executionIntent is required");
    }
    if (execution == null) {
      throw new Web3InvalidInputException("execution is required");
    }
  }

  public record Resource(String type, String id, String status) {

    public Resource {
      if (type == null || type.isBlank()) {
        throw new Web3InvalidInputException("resource.type is required");
      }
      if (id == null || id.isBlank()) {
        throw new Web3InvalidInputException("resource.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new Web3InvalidInputException("resource.status is required");
      }
    }
  }

  public record ExecutionIntent(
      String id, String status, LocalDateTime expiresAt, long expiresAtEpochSeconds) {

    public ExecutionIntent {
      if (id == null || id.isBlank()) {
        throw new Web3InvalidInputException("executionIntent.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new Web3InvalidInputException("executionIntent.status is required");
      }
      if (expiresAt == null) {
        throw new Web3InvalidInputException("executionIntent.expiresAt is required");
      }
      if (expiresAtEpochSeconds <= 0) {
        throw new Web3InvalidInputException("executionIntent.expiresAtEpochSeconds is required");
      }
    }
  }

  public record Execution(String mode, int signCount) {

    public Execution {
      if (mode == null || mode.isBlank()) {
        throw new Web3InvalidInputException("execution.mode is required");
      }
      if (signCount <= 0) {
        throw new Web3InvalidInputException("execution.signCount must be positive");
      }
    }
  }
}
