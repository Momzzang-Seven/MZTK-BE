package momzzangseven.mztkbe.modules.web3.qna.domain.model;

import java.math.BigInteger;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;

@Getter
@Builder(toBuilder = true)
public class QnaQuestionProjection {

  private final Long postId;
  private final String questionId;
  private final Long askerUserId;
  private final String tokenAddress;
  private final BigInteger rewardAmountWei;
  private final String questionHash;
  private final String acceptedAnswerId;
  private final int answerCount;
  private final QnaQuestionState state;

  public static QnaQuestionProjection create(
      Long postId,
      Long askerUserId,
      String questionId,
      String tokenAddress,
      BigInteger rewardAmountWei,
      String questionHash) {
    validateCore(postId, askerUserId, questionId, tokenAddress, rewardAmountWei, questionHash);
    return QnaQuestionProjection.builder()
        .postId(postId)
        .askerUserId(askerUserId)
        .questionId(questionId)
        .tokenAddress(tokenAddress)
        .rewardAmountWei(rewardAmountWei)
        .questionHash(questionHash)
        .acceptedAnswerId(QnaEscrowIdCodec.zeroBytes32())
        .answerCount(0)
        .state(QnaQuestionState.CREATED)
        .build();
  }

  public QnaQuestionProjection updateQuestionHash(String nextQuestionHash) {
    if (nextQuestionHash == null || nextQuestionHash.isBlank()) {
      throw new Web3InvalidInputException("questionHash is required");
    }
    return toBuilder().questionHash(nextQuestionHash).build();
  }

  public QnaQuestionProjection syncAnswerCount(int nextAnswerCount) {
    if (nextAnswerCount < 0) {
      throw new Web3InvalidInputException("answerCount must be >= 0");
    }
    QnaQuestionState nextState =
        nextAnswerCount == 0 ? QnaQuestionState.CREATED : QnaQuestionState.ANSWERED;
    return toBuilder().answerCount(nextAnswerCount).state(nextState).build();
  }

  public QnaQuestionProjection markAccepted(String acceptedAnswerId) {
    if (acceptedAnswerId == null || acceptedAnswerId.isBlank()) {
      throw new Web3InvalidInputException("acceptedAnswerId is required");
    }
    return toBuilder().acceptedAnswerId(acceptedAnswerId).state(QnaQuestionState.PAID_OUT).build();
  }

  public QnaQuestionProjection markAdminSettled(String acceptedAnswerId) {
    if (acceptedAnswerId == null || acceptedAnswerId.isBlank()) {
      throw new Web3InvalidInputException("acceptedAnswerId is required");
    }
    return toBuilder()
        .acceptedAnswerId(acceptedAnswerId)
        .state(QnaQuestionState.ADMIN_SETTLED)
        .build();
  }

  public QnaQuestionProjection markDeleted() {
    return toBuilder().state(QnaQuestionState.DELETED).build();
  }

  public QnaQuestionProjection markDeletedWithAnswers() {
    return toBuilder().state(QnaQuestionState.DELETED_WITH_ANSWERS).build();
  }

  private static void validateCore(
      Long postId,
      Long askerUserId,
      String questionId,
      String tokenAddress,
      BigInteger rewardAmountWei,
      String questionHash) {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (askerUserId == null || askerUserId <= 0) {
      throw new Web3InvalidInputException("askerUserId must be positive");
    }
    if (questionId == null || questionId.isBlank()) {
      throw new Web3InvalidInputException("questionId is required");
    }
    if (tokenAddress == null || tokenAddress.isBlank()) {
      throw new Web3InvalidInputException("tokenAddress is required");
    }
    if (rewardAmountWei == null || rewardAmountWei.signum() <= 0) {
      throw new Web3InvalidInputException("rewardAmountWei must be positive");
    }
    if (questionHash == null || questionHash.isBlank()) {
      throw new Web3InvalidInputException("questionHash is required");
    }
  }
}
