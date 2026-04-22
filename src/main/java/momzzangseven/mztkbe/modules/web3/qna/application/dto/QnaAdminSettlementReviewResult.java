package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record QnaAdminSettlementReviewResult(
    Long postId,
    Long answerId,
    boolean processable,
    QnaAdminReviewValidationCode blockingReason,
    QnaAdminExecutionAuthorityView authority,
    QnaAdminLocalQuestionView localQuestion,
    QnaAdminLocalAnswerView localAnswer,
    QnaAdminOnchainQuestionView onchainQuestion,
    QnaAdminOnchainAnswerView onchainAnswer,
    boolean questionHashMatches,
    boolean answerHashMatches,
    boolean questionConflictingActiveIntent,
    boolean answerConflictingActiveIntent,
    List<QnaAdminReviewValidationItem> validations) {

  public QnaAdminSettlementReviewResult {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (answerId == null || answerId <= 0) {
      throw new Web3InvalidInputException("answerId must be positive");
    }
    if (authority == null) {
      throw new Web3InvalidInputException("authority is required");
    }
    if (localQuestion == null
        || localAnswer == null
        || onchainQuestion == null
        || onchainAnswer == null) {
      throw new Web3InvalidInputException("review views are required");
    }
    if (validations == null) {
      throw new Web3InvalidInputException("validations are required");
    }
  }
}
