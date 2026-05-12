package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.time.LocalDateTime;

/**
 * Answer-module owned projection of answer lifecycle write payload.
 *
 * <p>This isolates answer APIs from the shared Web3 internal contract while preserving all fields
 * needed to drive client signing.
 */
public record AnswerExecutionWriteView(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequest signRequest,
    boolean existing,
    SignatureMeta signatureMeta) {

  /** Backward-compatible 6-arg constructor for callers that don't carry server-sig metadata. */
  public AnswerExecutionWriteView(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      SignRequest signRequest,
      boolean existing) {
    this(resource, actionType, executionIntent, execution, signRequest, existing, null);
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(String id, String status, LocalDateTime expiresAt) {}

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
