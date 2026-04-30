package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import java.util.Map;

public interface CountCommentsPort {
  Map<Long, Long> countCommentsByPostIds(List<Long> postIds);

  long countCommentsByPostId(Long postId);
}
