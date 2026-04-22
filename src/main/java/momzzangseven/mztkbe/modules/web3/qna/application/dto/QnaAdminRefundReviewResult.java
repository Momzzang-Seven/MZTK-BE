package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record QnaAdminRefundReviewResult(
    Long postId,
    boolean processable,
    QnaAdminReviewValidationCode blockingReason,
    QnaAdminExecutionAuthorityView authority,
    QnaAdminLocalQuestionView localQuestion,
    QnaAdminOnchainQuestionView onchainQuestion,
    boolean questionConflictingActiveIntent,
    boolean answerConflictingActiveIntent,
    List<QnaAdminReviewValidationItem> validations) {

  public QnaAdminRefundReviewResult {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (authority == null || localQuestion == null || onchainQuestion == null) {
      throw new Web3InvalidInputException("review views are required");
    }
    if (validations == null) {
      throw new Web3InvalidInputException("validations are required");
    }
  }
}
