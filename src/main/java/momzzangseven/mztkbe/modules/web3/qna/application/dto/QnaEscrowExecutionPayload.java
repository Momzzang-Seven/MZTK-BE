package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;

public record QnaEscrowExecutionPayload(
    QnaExecutionActionType actionType,
    Long postId,
    Long answerId,
    String authorityAddress,
    String tokenAddress,
    BigInteger amountWei,
    String questionHash,
    String contentHash,
    String callTarget,
    String callData,
    Long questionUpdateVersion,
    String questionUpdateToken,
    Long answerUpdateVersion,
    String answerUpdateToken,
    Long signedAt,
    String signatureHex) {

  public QnaEscrowExecutionPayload(
      QnaExecutionActionType actionType,
      Long postId,
      Long answerId,
      String authorityAddress,
      String tokenAddress,
      BigInteger amountWei,
      String questionHash,
      String contentHash,
      String callTarget,
      String callData) {
    this(
        actionType,
        postId,
        answerId,
        authorityAddress,
        tokenAddress,
        amountWei,
        questionHash,
        contentHash,
        callTarget,
        callData,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * Returns a server-sig-independent projection used for idempotency hashing.
   *
   * <p>{@code signedAt}, {@code signatureHex}, and the server-sig-embedded {@code callData} change
   * on every {@code prepare} call (KMS issues a new signature each time), so including them in the
   * payload hash makes {@code CreateExecutionIntentService.tryReuseExisting} treat every retry as a
   * conflicting payload and cancel the in-flight {@code AWAITING_SIGNATURE} intent. The fields kept
   * here uniquely identify the logical request (action + actors + amounts + content hashes + update
   * tokens), which is what idempotency should compare. Broadcast snapshot integrity is preserved
   * separately by {@code executionDigest} (EIP-7702) and {@code unsignedTxFingerprint} (EIP-1559).
   */
  public IdempotencyView idempotencyView() {
    return new IdempotencyView(
        actionType,
        postId,
        answerId,
        authorityAddress,
        tokenAddress,
        amountWei,
        questionHash,
        contentHash,
        callTarget,
        questionUpdateVersion,
        questionUpdateToken,
        answerUpdateVersion,
        answerUpdateToken);
  }

  /** Server-sig-independent projection. See {@link #idempotencyView()}. */
  public record IdempotencyView(
      QnaExecutionActionType actionType,
      Long postId,
      Long answerId,
      String authorityAddress,
      String tokenAddress,
      BigInteger amountWei,
      String questionHash,
      String contentHash,
      String callTarget,
      Long questionUpdateVersion,
      String questionUpdateToken,
      Long answerUpdateVersion,
      String answerUpdateToken) {}
}
