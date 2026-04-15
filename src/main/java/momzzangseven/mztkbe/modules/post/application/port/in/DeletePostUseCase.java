package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;

public interface DeletePostUseCase {
  PostMutationResult deletePost(Long userId, Long postId);
}
