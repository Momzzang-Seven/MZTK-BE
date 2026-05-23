package momzzangseven.mztkbe.modules.answer.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.modules.answer.application.port.in.CleanupAnswerPreparationUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPreparationCleanupPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CleanupAnswerPreparationService implements CleanupAnswerPreparationUseCase {

  private final AnswerPreparationCleanupPort answerPreparationCleanupPort;
  private final PublishAnswerDeletedEventPort publishAnswerDeletedEventPort;

  @Override
  @Transactional
  public CleanupAnswerPreparationResult cleanupExpiredPreparations(
      LocalDateTime now, int batchSize) {
    if (now == null) {
      throw new AnswerInvalidInputException("now is required.");
    }
    if (batchSize <= 0) {
      throw new AnswerInvalidInputException("batchSize must be positive.");
    }
    if (!answerPreparationCleanupPort.tryAcquirePreparationCleanupLock()) {
      return new CleanupAnswerPreparationResult(0, 0, 0);
    }
    List<Long> expiredCreateAnswerIds =
        answerPreparationCleanupPort.findExpiredCreatePreparationAnswerIds(now, batchSize);
    List<Long> deletedCreateAnswerIds =
        answerPreparationCleanupPort.deleteCreatePreparationAnswers(expiredCreateAnswerIds);
    deletedCreateAnswerIds.forEach(
        answerId -> publishAnswerDeletedEventPort.publish(new AnswerDeletedEvent(answerId)));
    int expiredDeletePreparations =
        answerPreparationCleanupPort.expireDeletePreparations(now, batchSize);
    int expiredUpdatePreparations =
        answerPreparationCleanupPort.expireUpdatePreparations(now, batchSize);
    return new CleanupAnswerPreparationResult(
        deletedCreateAnswerIds.size(), expiredDeletePreparations, expiredUpdatePreparations);
  }
}
