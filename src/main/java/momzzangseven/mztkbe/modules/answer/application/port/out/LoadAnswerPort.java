package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;

public interface LoadAnswerPort {
  List<Answer> loadAnswersByPostId(Long postId);
}
