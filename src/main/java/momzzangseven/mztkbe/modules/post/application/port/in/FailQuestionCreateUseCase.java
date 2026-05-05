package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.SyncQuestionPublicationStateCommand;

public interface FailQuestionCreateUseCase {

  void failQuestionCreate(SyncQuestionPublicationStateCommand command);
}
