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
    String questionUpdateToken) {

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
        null);
  }
}
