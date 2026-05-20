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
    String orderKey,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequest signRequest,
    String signRequestUnavailableReason,
    boolean existing,
    SignatureMeta signatureMeta,
    TokenMovement tokenMovement) {

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

  public ReservationExecutionWriteView(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      SignRequest signRequest,
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

  public ReservationExecutionWriteView asExistingForOrder(String orderKey) {
    return new ReservationExecutionWriteView(
        resource,
        actionType,
        orderKey,
        executionIntent,
        execution,
        signRequest,
        signRequestUnavailableReason,
        true,
        signatureMeta,
        tokenMovement);
  }

  /** Resource identity surfaced in the marketplace Web3 write response. */
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

  /** Execution intent metadata needed by clients to submit user signatures. */
  public record ExecutionIntent(
      String id, String status, LocalDateTime expiresAt, Long expiresAtEpochSeconds) {

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

    public ExecutionIntent(String id, String status, LocalDateTime expiresAt) {
      this(id, status, expiresAt, null);
    }
  }

  /** Execution mode and required signature count. */
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

  /** Sign request bundle for either EIP-7702 or EIP-1559 execution. */
  public record SignRequest(Authorization authorization, Submit submit, Transaction transaction) {}

  /** EIP-7702 authorization signature request. */
  public record Authorization(
      Long chainId, String delegateTarget, Long authorityNonce, String payloadHashToSign) {}

  /** EIP-7702 submit signature request. */
  public record Submit(String executionDigest, Long deadlineEpochSeconds) {}

  /** EIP-1559 transaction signature request. */
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

  public record SignatureMeta(Long signedAt, Long signatureExpiresAt) {}

  public record TokenMovement(
      String tokenAddress,
      String amountBaseUnits,
      String fromRole,
      String fromAddress,
      String toRole,
      String toAddress) {}
}
