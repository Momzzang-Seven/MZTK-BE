package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;

public interface QnaQuestionPublicationSyncPort {

  void confirmQuestionCreated(Long postId, String executionIntentId);

  void failQuestionCreate(
      Long postId,
      String executionIntentId,
      QnaExecutionIntentStatus terminalStatus,
      String failureReason);
}
