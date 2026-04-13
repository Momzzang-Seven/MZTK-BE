package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public interface AnswerEscrowExecutionUseCase {

  QnaExecutionIntentResult prepareAnswerCreate(PrepareAnswerCreateCommand command);

  QnaExecutionIntentResult prepareAnswerUpdate(PrepareAnswerUpdateCommand command);

  QnaExecutionIntentResult prepareAnswerDelete(PrepareAnswerDeleteCommand command);
}
