package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;

public interface GetAnswerUseCase {

  List<AnswerResult> execute(Long postId, Long currentUserId);
}
