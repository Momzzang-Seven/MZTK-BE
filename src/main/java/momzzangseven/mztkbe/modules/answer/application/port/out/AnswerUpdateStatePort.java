package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AnswerUpdateStatePort {

  AnswerUpdateState createPreparing(
      Long answerId, String pendingContent, String preparationToken, LocalDateTime expiresAt);

  int bindIntentIfCurrent(
      Long answerId,
      Long updateVersion,
      String updateToken,
      String preparationToken,
      String executionIntentId);

  Optional<AnswerUpdateState> loadIntentBoundState(
      Long answerId, Long updateVersion, String updateToken, String executionIntentId);

  Optional<AnswerUpdateState> loadLatestRecoverable(Long answerId);

  int markConfirmed(Long stateId);

  int markFailedIfCurrent(
      Long answerId,
      Long updateVersion,
      String updateToken,
      String executionIntentId,
      String errorReason);

  int discardLatestFailed(Long answerId);

  int bindRecoveryIntentIfCurrent(Long stateId, String executionIntentId);

  boolean hasBlockingUpdate(Long answerId);

  record AnswerUpdateState(
      Long id,
      Long answerId,
      Long updateVersion,
      String updateToken,
      String executionIntentId,
      String pendingContent,
      boolean pendingImageUpdate) {}
}
