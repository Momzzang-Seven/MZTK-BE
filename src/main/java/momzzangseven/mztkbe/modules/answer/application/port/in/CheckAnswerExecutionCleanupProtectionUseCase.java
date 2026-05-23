package momzzangseven.mztkbe.modules.answer.application.port.in;

public interface CheckAnswerExecutionCleanupProtectionUseCase {

  boolean hasCurrentCreateIntent(String executionIntentId);

  boolean hasFailedAnswer(Long answerId);

  boolean hasCurrentDeleteIntent(String executionIntentId);
}
