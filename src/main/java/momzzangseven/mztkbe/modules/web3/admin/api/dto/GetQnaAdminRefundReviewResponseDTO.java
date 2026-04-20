package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRefundReviewResult;
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

  public static GetQnaAdminRefundReviewResponseDTO from(QnaAdminRefundReviewResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return new GetQnaAdminRefundReviewResponseDTO(
        result.postId(),
        result.processable(),
        result.blockingReason(),
        result.authority(),
        result.localQuestion(),
        result.onchainQuestion(),
        result.questionConflictingActiveIntent(),
        result.answerConflictingActiveIntent(),
        result.validations());
  }
}
