package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;

public interface CreatePostUseCase {
  Long createPost(CreatePostCommand command); // 저장된 게시글 ID 반환
}
