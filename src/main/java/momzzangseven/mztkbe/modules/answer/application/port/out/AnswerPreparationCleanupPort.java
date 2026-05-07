package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

public interface AnswerPreparationCleanupPort {

  List<Long> findExpiredCreatePreparationAnswerIds(LocalDateTime now, int batchSize);

  List<Long> deleteCreatePreparationAnswers(List<Long> answerIds);

  int expireDeletePreparations(LocalDateTime now, int batchSize);

  int expireUpdatePreparations(LocalDateTime now, int batchSize);
}
