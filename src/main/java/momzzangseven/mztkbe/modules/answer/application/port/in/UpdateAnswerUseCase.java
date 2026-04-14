package momzzangseven.mztkbe.modules.answer.application.port.in;

import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;

public interface UpdateAnswerUseCase {

  void execute(UpdateAnswerCommand command);
}
