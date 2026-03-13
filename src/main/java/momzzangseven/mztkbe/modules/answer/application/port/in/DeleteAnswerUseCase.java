package momzzangseven.mztkbe.modules.answer.application.port.in;

import momzzangseven.mztkbe.modules.answer.application.dto.DeleteAnswerCommand;

public interface DeleteAnswerUseCase {

  void execute(DeleteAnswerCommand command);
}
