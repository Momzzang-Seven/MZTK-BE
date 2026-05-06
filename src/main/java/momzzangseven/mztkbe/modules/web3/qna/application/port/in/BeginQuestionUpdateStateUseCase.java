package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.BeginQuestionUpdateStateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QuestionUpdateStatePreparationResult;

public interface BeginQuestionUpdateStateUseCase {

  QuestionUpdateStatePreparationResult begin(BeginQuestionUpdateStateCommand command);
}
