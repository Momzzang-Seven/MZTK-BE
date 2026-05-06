package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.List;
import java.util.Map;

public interface CountAnswersUseCase {

  long countAnswers(Long postId);

  Map<Long, Long> countAnswersByPostIds(List<Long> postIds);
}
