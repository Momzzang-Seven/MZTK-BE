package momzzangseven.mztkbe.modules.comment.application.port.in;

import java.util.List;
import java.util.Map;

public interface CountCommentsUseCase {

  long countCommentsByPostId(Long postId);

  Map<Long, Long> countCommentsByPostIds(List<Long> postIds);
}
