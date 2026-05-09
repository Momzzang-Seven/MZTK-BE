package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;

public interface AnswerPublicationReconciliationStatePort {

  boolean tryAcquireReconciliationLock();

  List<CreateCandidate> findPendingSubmitCandidates(int batchSize);

  int confirmSubmitIfCurrent(Long answerId, String executionIntentId);

  int failSubmitIfCurrent(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason);

  List<UpdateCandidate> findIntentBoundUpdateCandidates(int batchSize);

  int applyConfirmedUpdateContentIfCurrent(
      Long stateId, Long answerId, String executionIntentId, String pendingContent);

  int markUpdateConfirmedIfCurrent(Long stateId, String executionIntentId);

  int failUpdateIfCurrent(
      Long stateId, String executionIntentId, String terminalStatus, String failureReason);

  int markUpdateReconciliationRequiredIfCurrent(
      Long stateId, String executionIntentId, String reason);

  List<DeleteCandidate> findPendingDeleteCandidates(int batchSize);

  Long deleteConfirmedDeleteAnswer(DeleteCandidate candidate);

  int rollbackDeleteIfCurrent(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason);

  record CreateCandidate(Long answerId, String executionIntentId) {}

  record UpdateCandidate(
      Long stateId,
      Long answerId,
      Long answerUserId,
      String executionIntentId,
      String pendingContent) {}

  record DeleteCandidate(Long answerId, String executionIntentId) {}
}
