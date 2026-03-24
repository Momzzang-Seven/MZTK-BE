package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;

public interface GetPostUseCase {

  PostDetailResult getPost(Long postId);
}
