package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Collection;
import java.util.Map;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;

public interface LoadAnswerImagesPort {

  Map<Long, AnswerImageResult> loadImagesByAnswerIds(Collection<Long> answerIds);
}
