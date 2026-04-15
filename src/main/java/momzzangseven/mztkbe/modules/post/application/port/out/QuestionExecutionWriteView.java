package momzzangseven.mztkbe.modules.post.application.port.out;

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
    boolean existing) {

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(String id, String status, LocalDateTime expiresAt) {}

  public record Execution(String mode, int signCount) {}

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
