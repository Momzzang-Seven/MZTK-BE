package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.answer;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.CheckAnswerExecutionCleanupProtectionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.CheckQnaAnswerCleanupProtectionPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QnaAnswerCleanupProtectionAdapter implements CheckQnaAnswerCleanupProtectionPort {

  private final CheckAnswerExecutionCleanupProtectionUseCase protectionUseCase;

  @Override
  public boolean hasCurrentCreateIntent(String executionIntentId) {
    return protectionUseCase.hasCurrentCreateIntent(executionIntentId);
  }

  @Override
  public boolean hasFailedAnswer(Long answerId) {
    return protectionUseCase.hasFailedAnswer(answerId);
  }

  @Override
  public boolean hasCurrentDeleteIntent(String executionIntentId) {
    return protectionUseCase.hasCurrentDeleteIntent(executionIntentId);
  }
}
