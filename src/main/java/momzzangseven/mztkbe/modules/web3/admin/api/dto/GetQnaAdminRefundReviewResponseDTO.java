package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminReviewValidationItem;

public record GetQnaAdminRefundReviewResponseDTO(
    Long postId,
    boolean processable,
    String blockingReason,
    QnaAdminExecutionAuthorityView authority,
    QnaAdminLocalQuestionView localQuestion,
    QnaAdminOnchainQuestionView onchainQuestion,
    boolean questionConflictingActiveIntent,
    boolean answerConflictingActiveIntent,
    List<QnaAdminReviewValidationItem> validations) {

  public static GetQnaAdminRefundReviewResponseDTO from(GetQnaAdminRefundReviewResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    var review = result.review();
    return new GetQnaAdminRefundReviewResponseDTO(
        review.postId(),
        review.processable(),
        review.blockingReason(),
        review.authority(),
        review.localQuestion(),
        review.onchainQuestion(),
        review.questionConflictingActiveIntent(),
        review.answerConflictingActiveIntent(),
        review.validations());
  }
}
