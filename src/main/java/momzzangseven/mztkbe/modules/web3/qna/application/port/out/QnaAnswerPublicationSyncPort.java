package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

public interface QnaAnswerPublicationSyncPort {

  void confirmAnswerSubmitted(Long answerId, String executionIntentId);

  void failAnswerSubmit(
      Long answerId,
      String executionIntentId,
      ExecutionIntentStatus terminalStatus,
      String failureReason);
}
