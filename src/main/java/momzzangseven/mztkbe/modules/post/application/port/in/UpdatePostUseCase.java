package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.api.dto.UpdatePostRequest;

public interface UpdatePostUseCase {
  void updatePost(Long userId, Long postId, UpdatePostRequest request);
}
