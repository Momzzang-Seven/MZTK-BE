package momzzangseven.mztkbe.modules.answer.application.port.in;

import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;

public interface CreateAnswerUseCase {

  Long createAnswer(CreateAnswerCommand command);
}
