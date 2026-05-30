package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.PendingXpGrant;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationCommand;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationResult;
import momzzangseven.mztkbe.modules.level.application.port.in.RunXpGrantReconciliationUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.XpGrantOutboxPort;
import org.springframework.stereotype.Service;

/**
 * Drives one reconciliation pass: reads due rows, then processes each in its own transaction via
 * {@link XpGrantOutboxProcessor}. Intentionally <b>not</b> {@code @Transactional} — each row must
 * commit (or roll back) independently, and a single failing row must not abort the whole batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XpGrantReconciliationService implements RunXpGrantReconciliationUseCase {

  private final XpGrantOutboxPort outboxPort;
  private final XpGrantOutboxProcessor processor;

  @Override
  public RunXpGrantReconciliationResult run(RunXpGrantReconciliationCommand command) {
    List<PendingXpGrant> due = outboxPort.findDueBatch(LocalDateTime.now(), command.batchSize());

    int granted = 0;
    int skipped = 0;
    int failed = 0;
    for (PendingXpGrant pending : due) {
      try {
        if (processor.process(pending.id())) {
          granted++;
        } else {
          skipped++;
        }
      } catch (Exception e) {
        failed++;
        outboxPort.recordFailure(
            pending.id(), command.maxAttempts(), command.backoffSeconds(), e.getMessage());
        log.error(
            "XP grant reconciliation attempt failed: id={}, attempt={}",
            pending.id(),
            pending.attemptCount() + 1,
            e);
      }
    }

    return new RunXpGrantReconciliationResult(due.size(), granted, skipped, failed);
  }
}
