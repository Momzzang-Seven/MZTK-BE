package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

public interface QnaQuestionPublicationSyncPort {

  void confirmQuestionCreated(Long postId, String executionIntentId);

  void failQuestionCreate(
      Long postId,
      String executionIntentId,
      ExecutionIntentStatus terminalStatus,
      String failureReason);
}
