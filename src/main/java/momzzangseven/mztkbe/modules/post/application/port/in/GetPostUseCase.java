package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.PostResult;

public interface GetPostUseCase {

  PostResult getPost(Long postId);
}
