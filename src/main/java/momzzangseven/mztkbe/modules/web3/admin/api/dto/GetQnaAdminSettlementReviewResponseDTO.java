package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminReviewValidationItem;

public record GetQnaAdminSettlementReviewResponseDTO(
    Long postId,
    Long answerId,
    boolean processable,
    String blockingReason,
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

  public static GetQnaAdminSettlementReviewResponseDTO from(
      GetQnaAdminSettlementReviewResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    var review = result.review();
    return new GetQnaAdminSettlementReviewResponseDTO(
        review.postId(),
        review.answerId(),
        review.processable(),
        review.blockingReason(),
        review.authority(),
        review.localQuestion(),
        review.localAnswer(),
        review.onchainQuestion(),
        review.onchainAnswer(),
        review.questionHashMatches(),
        review.answerHashMatches(),
        review.questionConflictingActiveIntent(),
        review.answerConflictingActiveIntent(),
        review.validations());
  }
}
