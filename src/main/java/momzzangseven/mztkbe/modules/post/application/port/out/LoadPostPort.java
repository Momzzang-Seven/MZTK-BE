package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.Post;

public interface LoadPostPort {
  Optional<Post> loadPost(Long postId); // ID로 게시글 하나 찾기
}
