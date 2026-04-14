package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public interface AnswerEscrowExecutionUseCase {

  boolean hasActiveAnswerIntent(Long answerId);

  void precheckAnswerCreate(PrecheckAnswerCreateCommand command);

  QnaExecutionIntentResult prepareAnswerCreate(PrepareAnswerCreateCommand command);

  QnaExecutionIntentResult recoverAnswerCreate(PrepareAnswerCreateCommand command);

  java.util.Optional<QnaExecutionIntentResult> recoverAnswerUpdate(
      PrepareAnswerUpdateCommand command);

  QnaExecutionIntentResult prepareAnswerUpdate(PrepareAnswerUpdateCommand command);

  QnaExecutionIntentResult prepareAnswerDelete(PrepareAnswerDeleteCommand command);
}
