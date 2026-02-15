package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;

public interface LoadTagPort {
  List<Long> findPostIdsByTagName(String tagName);

  List<String> findTagNamesByPostId(Long postId);
}
