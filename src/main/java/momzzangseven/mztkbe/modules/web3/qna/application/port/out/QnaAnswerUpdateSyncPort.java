package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface QnaAnswerUpdateSyncPort {

  void confirmAnswerUpdate(
      Long answerId, Long updateVersion, String updateToken, String executionIntentId);

  void failAnswerUpdate(
      Long answerId,
      Long updateVersion,
      String updateToken,
      String executionIntentId,
      String failureReason);
}
