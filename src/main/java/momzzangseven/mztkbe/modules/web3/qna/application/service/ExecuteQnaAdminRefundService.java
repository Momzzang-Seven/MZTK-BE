package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort;

@RequiredArgsConstructor
public class ExecuteQnaAdminRefundService implements ExecuteQnaAdminRefundUseCase {

  private final LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort;
  private final PrepareQnaAdminRefundUseCase prepareQnaAdminRefundUseCase;

  @Override
  public QnaExecutionIntentResult execute(ExecuteQnaAdminRefundCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();

    var context = loadQnaAdminReviewContextPort.loadRefundForUpdate(command.postId());
    var review = QnaAdminReviewDecider.assessRefund(command.postId(), context);
    if (!review.processable()) {
      throwValidationFailure(review.blockingReason());
    }

    var localQuestion =
        context
            .localQuestion()
            .orElseThrow(() -> new Web3InvalidInputException("local question is required"));
    return prepareQnaAdminRefundUseCase.execute(
        new PrepareAdminRefundCommand(command.postId(), localQuestion.writerUserId()));
  }

  private void throwValidationFailure(String blockingReason) {
    if (QnaAdminReviewDecider.isBadRequestCode(blockingReason)) {
      throw new Web3InvalidInputException(blockingReason);
    }
    throw new Web3TransactionStateInvalidException(blockingReason);
  }
}
