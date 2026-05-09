package momzzangseven.mztkbe.modules.answer.application.port.in;

import momzzangseven.mztkbe.modules.answer.application.dto.SyncAnswerPublicationStateCommand;

public interface ConfirmAnswerSubmittedUseCase {

  void confirmAnswerSubmitted(SyncAnswerPublicationStateCommand command);
}
