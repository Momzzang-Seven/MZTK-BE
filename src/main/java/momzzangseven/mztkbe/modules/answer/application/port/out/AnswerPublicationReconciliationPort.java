package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;

public interface AnswerPublicationReconciliationPort {

  boolean tryAcquireReconciliationLock();

  int reconcileConfirmedSubmits(int batchSize);

  int reconcileTerminalSubmitFailures(int batchSize);

  int reconcileConfirmedUpdates(int batchSize);

  int reconcileTerminalUpdateFailures(int batchSize);

  List<DeleteCandidate> findConfirmedDeleteCandidates(int batchSize);

  List<Long> deleteConfirmedDeleteAnswers(List<DeleteCandidate> candidates);

  int reconcileTerminalDeleteRollbacks(int batchSize);

  int repairQuestionAnswerCounts();

  record DeleteCandidate(Long answerId, String executionIntentId) {}
}
