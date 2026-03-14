package momzzangseven.mztkbe.modules.answer.application.port.in;

import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;

public interface CreateAnswerUseCase {

  CreateAnswerResult execute(CreateAnswerCommand command);
}
