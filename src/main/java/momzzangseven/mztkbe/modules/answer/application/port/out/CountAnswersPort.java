package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;
import java.util.Map;

public interface CountAnswersPort {

  long countAnswers(Long postId);

  Map<Long, Long> countAnswersByPostIds(List<Long> postIds);
}
