package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;

public interface UpdatePostUseCase {
  PostMutationResult updatePost(Long userId, Long postId, UpdatePostCommand command);
}
