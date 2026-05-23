package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;

public interface QnaAnswerPublicationSyncPort {

  void confirmAnswerSubmitted(Long answerId, String executionIntentId);

  void failAnswerSubmit(
      Long answerId,
      String executionIntentId,
      QnaExecutionIntentStatus terminalStatus,
      String failureReason);
}
