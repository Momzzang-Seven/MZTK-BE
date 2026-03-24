package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.port.in.RunOrphanAnswerCleanupBatchUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.answer.infrastructure.config.AnswerOrphanCleanupProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerOrphanCleanupService implements RunOrphanAnswerCleanupBatchUseCase {

  private final LoadAnswerPort loadAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;
  private final ApplicationEventPublisher eventPublisher;
  private final AnswerOrphanCleanupProperties props;

  @Override
  @Transactional
  public int runBatch() {
    validateProperties();
    List<Long> orphanAnswerIds = loadAnswerPort.loadOrphanAnswerIds(props.getBatchSize());
    if (orphanAnswerIds.isEmpty()) {
      return 0;
    }

    deleteAnswerPort.deleteAnswersByIds(orphanAnswerIds);
    orphanAnswerIds.forEach(
        answerId -> eventPublisher.publishEvent(new AnswerDeletedEvent(answerId)));

    log.info("Orphan answer cleanup batch: deleted={}", orphanAnswerIds.size());
    return orphanAnswerIds.size();
  }

  private void validateProperties() {
    if (props.getBatchSize() <= 0) {
      throw new IllegalStateException("answer.orphan-cleanup.batch-size must be positive");
    }
  }
}
