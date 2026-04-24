package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import java.util.Map;

public interface CountCommentsPort {

  long countCommentsByPostId(Long postId);

  Map<Long, Long> countCommentsByPostIds(List<Long> postIds);
}
