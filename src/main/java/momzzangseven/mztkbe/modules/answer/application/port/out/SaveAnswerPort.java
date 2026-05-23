package momzzangseven.mztkbe.modules.answer.application.port.out;

import momzzangseven.mztkbe.modules.answer.domain.model.Answer;

public interface SaveAnswerPort {
  Answer saveAnswer(Answer answer);

  int bindCreateIntentIfCurrent(Long answerId, String preparationToken, String executionIntentId);

  int confirmCreateIfCurrent(Long answerId, String executionIntentId);

  int markCreateFailedIfCurrent(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason);

  int bindDeleteIntentIfCurrent(Long answerId, String preparationToken, String executionIntentId);

  int rollbackDeleteIfCurrent(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason);

  int rollbackDeletePreparationIfCurrent(
      Long answerId, String preparationToken, String terminalStatus, String failureReason);

  int markDeleteSyncConflictIfMismatched(
      Long answerId, String executionIntentId, String conflictReason);
}
