package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface CheckQnaAnswerCleanupProtectionPort {

  boolean hasCurrentCreateIntent(String executionIntentId);

  boolean hasFailedAnswer(Long answerId);

  boolean hasCurrentDeleteIntent(String executionIntentId);
}
