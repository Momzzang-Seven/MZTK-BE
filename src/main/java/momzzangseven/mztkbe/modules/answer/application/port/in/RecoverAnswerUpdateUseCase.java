package momzzangseven.mztkbe.modules.answer.application.port.in;

import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerUpdateCommand;

public interface RecoverAnswerUpdateUseCase {

  AnswerMutationResult recoverAnswerUpdate(RecoverAnswerUpdateCommand command);
}
