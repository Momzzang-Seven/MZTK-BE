package momzzangseven.mztkbe.modules.answer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.CheckAnswerExecutionCleanupProtectionUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckAnswerExecutionCleanupProtectionService
    implements CheckAnswerExecutionCleanupProtectionUseCase {

  private final LoadAnswerPort loadAnswerPort;

  @Override
  public boolean hasCurrentCreateIntent(String executionIntentId) {
    return executionIntentId != null
        && loadAnswerPort.existsByCurrentCreateExecutionIntentId(executionIntentId);
  }

  @Override
  public boolean hasFailedAnswer(Long answerId) {
    return answerId != null && loadAnswerPort.existsFailedAnswerById(answerId);
  }

  @Override
  public boolean hasCurrentDeleteIntent(String executionIntentId) {
    return executionIntentId != null
        && loadAnswerPort.existsByCurrentDeleteExecutionIntentId(executionIntentId);
  }
}
