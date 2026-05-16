package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Marketplace-owned execution intent result exposed across module boundaries. */
public record MarketplaceExecutionIntentResult(
    Resource resource,
    String actionType,
    String orderKey,
    ExecutionIntent executionIntent,
    Execution execution,
    MarketplaceSignRequest signRequest,
    String signRequestUnavailableReason,
    boolean existing,
    MarketplaceSignatureMeta signatureMeta,
    MarketplaceTokenMovement tokenMovement) {

  private static final String AWAITING_SIGNATURE = "AWAITING_SIGNATURE";

  public MarketplaceExecutionIntentResult {
    if (resource == null) {
      throw new Web3InvalidInputException("resource is required");
    }
    if (actionType == null || actionType.isBlank()) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (orderKey == null || orderKey.isBlank()) {
      orderKey = null;
    }
    if (executionIntent == null) {
      throw new Web3InvalidInputException("executionIntent is required");
    }
    if (execution == null) {
      throw new Web3InvalidInputException("execution is required");
    }
    if (signRequest != null && signRequestUnavailableReason != null) {
      throw new Web3InvalidInputException(
          "signRequest and signRequestUnavailableReason cannot both be present");
    }
    if (AWAITING_SIGNATURE.equals(executionIntent.status()) && signRequest == null) {
      throw new Web3InvalidInputException("signRequest is required for AWAITING_SIGNATURE");
    }
  }

  public MarketplaceExecutionIntentResult(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      MarketplaceSignRequest signRequest,
      String signRequestUnavailableReason,
      boolean existing) {
    this(
        resource,
        actionType,
        null,
        executionIntent,
        execution,
        signRequest,
        signRequestUnavailableReason,
        existing,
        null,
        null);
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
      String id, String status, LocalDateTime expiresAt, Long expiresAtEpochSeconds) {

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
    }

    public ExecutionIntent(String id, String status, LocalDateTime expiresAt) {
      this(id, status, expiresAt, null);
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
