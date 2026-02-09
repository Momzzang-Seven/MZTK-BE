package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.api.dto.PostResponse;

public interface GetPostUseCase {
  PostResponse getPost(Long postId);
}
