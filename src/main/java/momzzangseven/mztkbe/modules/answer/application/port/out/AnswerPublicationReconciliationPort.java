package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;

public interface AnswerPublicationReconciliationPort {

  int reconcileConfirmedSubmits(int batchSize);

  int reconcileTerminalSubmitFailures(int batchSize);

  int reconcileConfirmedUpdates(int batchSize);

  int reconcileTerminalUpdateFailures(int batchSize);

  List<Long> findConfirmedDeleteAnswerIds(int batchSize);

  int deleteConfirmedDeleteAnswers(List<Long> answerIds);

  int reconcileTerminalDeleteRollbacks(int batchSize);

  int repairQuestionAnswerCounts();
}
