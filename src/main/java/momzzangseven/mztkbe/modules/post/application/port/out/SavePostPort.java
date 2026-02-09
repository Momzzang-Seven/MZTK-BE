package momzzangseven.mztkbe.modules.post.application.port.out;

import momzzangseven.mztkbe.modules.post.domain.model.Post;

public interface SavePostPort {
  Post savePost(Post post); // 게시글 저장/수정

  void deletePost(Post post); // 게시글 삭제
}
