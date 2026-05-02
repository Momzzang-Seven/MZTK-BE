package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;

public interface BlockPostUseCase {

  void blockPost(ModeratePostCommand command);
}
