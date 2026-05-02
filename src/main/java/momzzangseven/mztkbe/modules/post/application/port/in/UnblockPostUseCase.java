package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;

public interface UnblockPostUseCase {

  void unblockPost(ModeratePostCommand command);
}
