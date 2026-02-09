package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.domain.model.Post;

public interface GetPostUseCase {
  Post getPost(Long postId);
}
