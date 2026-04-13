package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public interface QuestionEscrowExecutionUseCase {

  void precheckQuestionCreate(PrecheckQuestionCreateCommand command);

  QnaExecutionIntentResult prepareQuestionCreate(PrepareQuestionCreateCommand command);

  QnaExecutionIntentResult prepareQuestionUpdate(PrepareQuestionUpdateCommand command);

  QnaExecutionIntentResult prepareQuestionDelete(PrepareQuestionDeleteCommand command);

  QnaExecutionIntentResult prepareAnswerAccept(PrepareAnswerAcceptCommand command);
}
