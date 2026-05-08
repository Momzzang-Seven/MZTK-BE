package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;
import java.util.Map;

public interface CountAnswerCommentsPort {

  Map<Long, Long> countCommentsByAnswerIds(List<Long> answerIds);
}
