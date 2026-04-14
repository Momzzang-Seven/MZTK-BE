package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerOrphanCleanupBatchSizePort;
import momzzangseven.mztkbe.modules.answer.infrastructure.config.AnswerOrphanCleanupProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerOrphanCleanupConfigAdapter implements LoadAnswerOrphanCleanupBatchSizePort {

  private final AnswerOrphanCleanupProperties properties;

  @Override
  public int loadBatchSize() {
    return properties.getBatchSize();
  }
}
