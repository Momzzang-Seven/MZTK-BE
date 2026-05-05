package momzzangseven.mztkbe.modules.comment.application.port.in;

import java.util.List;
import java.util.Map;

public interface CountCommentsUseCase {

  Map<Long, Long> countCommentsByPostIds(List<Long> postIds);

  long countCommentsByPostId(Long postId);

  Map<Long, Long> countCommentsByAnswerIds(List<Long> answerIds);

  long countCommentsByAnswerId(Long answerId);
}
