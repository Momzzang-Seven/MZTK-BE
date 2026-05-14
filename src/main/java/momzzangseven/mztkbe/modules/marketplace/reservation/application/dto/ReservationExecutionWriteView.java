package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;

/**
 * Reservation-owned projection of the marketplace Web3 write payload.
 *
 * <p>This keeps reservation application code independent from {@code web3/marketplace} DTOs.
 */
public record ReservationExecutionWriteView(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequest signRequest,
    String signRequestUnavailableReason,
    boolean existing) {

  public ReservationExecutionWriteView {
    if (resource == null) {
      throw new IllegalArgumentException("resource is required");
    }
    if (actionType == null || actionType.isBlank()) {
      throw new IllegalArgumentException("actionType is required");
    }
    if (executionIntent == null) {
      throw new IllegalArgumentException("executionIntent is required");
    }
    if (execution == null) {
      throw new IllegalArgumentException("execution is required");
    }
    if (signRequest != null && signRequestUnavailableReason != null) {
      throw new IllegalArgumentException(
          "signRequest and signRequestUnavailableReason cannot both be present");
    }
  }

  public record Resource(String type, String id, String status) {

    public Resource {
      if (type == null || type.isBlank()) {
        throw new IllegalArgumentException("resource.type is required");
      }
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("resource.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new IllegalArgumentException("resource.status is required");
      }
    }
  }

  public record ExecutionIntent(String id, String status, LocalDateTime expiresAt) {

    public ExecutionIntent {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("executionIntent.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new IllegalArgumentException("executionIntent.status is required");
      }
      if (expiresAt == null) {
        throw new IllegalArgumentException("executionIntent.expiresAt is required");
      }
    }
  }

  public record Execution(String mode, int signCount) {

    public Execution {
      if (mode == null || mode.isBlank()) {
        throw new IllegalArgumentException("execution.mode is required");
      }
      if (signCount <= 0) {
        throw new IllegalArgumentException("execution.signCount must be positive");
      }
    }
  }

  public record SignRequest(Authorization authorization, Submit submit, Transaction transaction) {}

  public record Authorization(
      Long chainId, String delegateTarget, Long authorityNonce, String payloadHashToSign) {}

  public record Submit(String executionDigest, Long deadlineEpochSeconds) {}

  public record Transaction(
      Long chainId,
      String fromAddress,
      String toAddress,
      String valueHex,
      String data,
      Long nonce,
      String gasLimitHex,
      String maxPriorityFeePerGasHex,
      String maxFeePerGasHex,
      Long expectedNonce) {}
}
