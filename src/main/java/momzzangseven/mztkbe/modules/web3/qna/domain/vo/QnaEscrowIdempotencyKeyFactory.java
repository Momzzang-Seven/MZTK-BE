package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public final class QnaEscrowIdempotencyKeyFactory {

  private QnaEscrowIdempotencyKeyFactory() {}

  public static String create(
      QnaExecutionActionType actionType, Long requesterUserId, Long postId, Long answerId) {
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }

    String suffix = answerId == null ? "" : ":" + answerId;
    return "qna:" + actionType.name().toLowerCase() + ":" + requesterUserId + ":" + postId + suffix;
  }
}
