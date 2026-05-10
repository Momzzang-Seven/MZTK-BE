package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.SyncQuestionPublicationStateCommand;

public interface ConfirmQuestionCreatedUseCase {

  void confirmQuestionCreated(SyncQuestionPublicationStateCommand command);
}
