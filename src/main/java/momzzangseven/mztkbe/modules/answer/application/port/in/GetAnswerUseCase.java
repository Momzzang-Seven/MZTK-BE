package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;

public interface GetAnswerUseCase {
  List<Answer> getAnswersByPostId(Long postId);
}
