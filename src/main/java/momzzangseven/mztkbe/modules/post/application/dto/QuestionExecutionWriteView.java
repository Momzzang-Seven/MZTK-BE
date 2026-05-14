package momzzangseven.mztkbe.modules.post.application.dto;

import java.time.LocalDateTime;

/**
 * Post-module owned projection of question lifecycle write payload.
 *
 * <p>This view keeps the post module decoupled from shared Web3 DTOs while still exposing the
 * signing contract needed by API responses.
 */
public record QuestionExecutionWriteView(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequest signRequest,
    String signRequestUnavailableReason,
    boolean existing,
    SignatureMeta signatureMeta) {

  /**
   * Backward-compatible constructor for callers that carry sign-request availability but not
   * server-sig metadata.
   */
  public QuestionExecutionWriteView(
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
        executionIntent,
        execution,
        signRequest,
        signRequestUnavailableReason,
        existing,
        null);
  }

  /** Backward-compatible 6-arg constructor for callers that don't carry server-sig metadata. */
  public QuestionExecutionWriteView(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      SignRequest signRequest,
      boolean existing) {
    this(resource, actionType, executionIntent, execution, signRequest, null, existing, null);
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(
      String id, String status, LocalDateTime expiresAt, long expiresAtEpochSeconds) {}

  public record Execution(String mode, int signCount) {}

  public record SignRequest(Authorization authorization, Submit submit, Transaction transaction) {}

  /**
   * Server-sig metadata surfaced to the API client. Carries the epoch-second timestamp used in the
   * embedded EIP-712 signature and the derived expiry, so the FE can detect imminent expiry and
   * trigger a re-prepare without ABI-decoding the calldata.
   */
  public record SignatureMeta(Long signedAt, Long signatureExpiresAt) {}

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
