package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminSettlementReviewResult;

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
      QnaAdminSettlementReviewResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return new GetQnaAdminSettlementReviewResponseDTO(
        result.postId(),
        result.answerId(),
        result.processable(),
        result.blockingReason(),
        result.authority(),
        result.localQuestion(),
        result.localAnswer(),
        result.onchainQuestion(),
        result.onchainAnswer(),
        result.questionHashMatches(),
        result.answerHashMatches(),
        result.questionConflictingActiveIntent(),
        result.answerConflictingActiveIntent(),
        result.validations());
  }
}
