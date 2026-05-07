package momzzangseven.mztkbe.modules.answer.application.port.in;

import momzzangseven.mztkbe.modules.answer.application.dto.SyncAnswerUpdateStateCommand;

public interface FailAnswerUpdateUseCase {

  void failAnswerUpdate(SyncAnswerUpdateStateCommand command);
}
