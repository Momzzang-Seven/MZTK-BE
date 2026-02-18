package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;

public interface UpdatePostUseCase {
  void updatePost(Long userId, Long postId, UpdatePostCommand command);
}
