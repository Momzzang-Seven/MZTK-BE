package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;

@RequiredArgsConstructor
public class ExecuteQnaAdminSettlementService implements ExecuteQnaAdminSettlementUseCase {

  private final LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort;
  private final QnaAcceptStateSyncPort qnaAcceptStateSyncPort;
  private final PrepareQnaAdminSettlementUseCase prepareQnaAdminSettlementUseCase;

  @Override
  public QnaExecutionIntentResult execute(ExecuteQnaAdminSettlementCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();

    var context =
        loadQnaAdminReviewContextPort.loadSettlementForUpdate(command.postId(), command.answerId());
    var review =
        QnaAdminReviewDecider.assessSettlement(command.postId(), command.answerId(), context);
    if (!review.processable()) {
      throwValidationFailure(review.blockingReason());
    }

    var localQuestion =
        context
            .localQuestion()
            .orElseThrow(() -> new Web3InvalidInputException("local question is required"));
    var localAnswer =
        context
            .localAnswer()
            .orElseThrow(() -> new Web3InvalidInputException("local answer is required"));

    qnaAcceptStateSyncPort.beginPendingAccept(command.postId(), command.answerId());
    return prepareQnaAdminSettlementUseCase.execute(
        new PrepareAdminSettleCommand(
            command.postId(),
            command.answerId(),
            localQuestion.writerUserId(),
            localAnswer.writerUserId(),
            localQuestion.content(),
            localAnswer.content()));
  }

  private void throwValidationFailure(String blockingReason) {
    if (QnaAdminReviewDecider.isBadRequestCode(blockingReason)) {
      throw new Web3InvalidInputException(blockingReason);
    }
    throw new Web3TransactionStateInvalidException(blockingReason);
  }
}
