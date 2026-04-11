package momzzangseven.mztkbe.modules.web3.qna.domain.model;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

@Getter
@Builder(toBuilder = true)
public class QnaAnswerProjection {

  private final Long answerId;
  private final Long postId;
  private final String questionId;
  private final String answerKey;
  private final Long responderUserId;
  private final String contentHash;

  public static QnaAnswerProjection create(
      Long answerId,
      Long postId,
      String questionId,
      String answerKey,
      Long responderUserId,
      String contentHash) {
    validate(answerId, postId, questionId, answerKey, responderUserId, contentHash);
    return QnaAnswerProjection.builder()
        .answerId(answerId)
        .postId(postId)
        .questionId(questionId)
        .answerKey(answerKey)
        .responderUserId(responderUserId)
        .contentHash(contentHash)
        .build();
  }

  public QnaAnswerProjection updateContentHash(String nextContentHash) {
    if (nextContentHash == null || nextContentHash.isBlank()) {
      throw new Web3InvalidInputException("contentHash is required");
    }
    return toBuilder().contentHash(nextContentHash).build();
  }

  private static void validate(
      Long answerId,
      Long postId,
      String questionId,
      String answerKey,
      Long responderUserId,
      String contentHash) {
    if (answerId == null || answerId <= 0) {
      throw new Web3InvalidInputException("answerId must be positive");
    }
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (questionId == null || questionId.isBlank()) {
      throw new Web3InvalidInputException("questionId is required");
    }
    if (answerKey == null || answerKey.isBlank()) {
      throw new Web3InvalidInputException("answerKey is required");
    }
    if (responderUserId == null || responderUserId <= 0) {
      throw new Web3InvalidInputException("responderUserId must be positive");
    }
    if (contentHash == null || contentHash.isBlank()) {
      throw new Web3InvalidInputException("contentHash is required");
    }
  }
}
