package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostResult;

public interface BlockPostUseCase {

  ModeratePostResult blockPost(ModeratePostCommand command);
}
