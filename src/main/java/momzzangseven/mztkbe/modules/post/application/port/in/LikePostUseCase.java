package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.LikePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.PostLikeResult;

public interface LikePostUseCase {

  PostLikeResult like(LikePostCommand command);

  PostLikeResult unlike(LikePostCommand command);
}
