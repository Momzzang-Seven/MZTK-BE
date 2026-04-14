package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.port.in.RunOrphanAnswerCleanupBatchUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerOrphanCleanupBatchSizePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerOrphanCleanupService implements RunOrphanAnswerCleanupBatchUseCase {

  private final LoadAnswerPort loadAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;
  private final PublishAnswerDeletedEventPort publishAnswerDeletedEventPort;
  private final LoadAnswerOrphanCleanupBatchSizePort loadBatchSizePort;

  @Override
  @Transactional
  public int runBatch() {
    int batchSize = loadBatchSizePort.loadBatchSize();
    validateBatchSize(batchSize);

    List<Long> orphanAnswerIds = loadAnswerPort.loadOrphanAnswerIds(batchSize);
    if (orphanAnswerIds.isEmpty()) {
      return 0;
    }

    deleteAnswerPort.deleteAnswersByIds(orphanAnswerIds);
    orphanAnswerIds.forEach(
        answerId -> publishAnswerDeletedEventPort.publish(new AnswerDeletedEvent(answerId)));

    log.info("Orphan answer cleanup batch: deleted={}", orphanAnswerIds.size());
    return orphanAnswerIds.size();
  }

  private void validateBatchSize(int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalStateException("answer.orphan-cleanup.batch-size must be positive");
    }
  }
}
