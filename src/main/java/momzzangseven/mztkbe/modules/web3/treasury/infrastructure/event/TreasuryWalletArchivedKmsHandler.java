package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ScheduleKmsKeyDeletionCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ScheduleKmsKeyDeletionUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler that drives the KMS {@code ScheduleKeyDeletion} side effect once the
 * cohort's DB rows have transitioned to {@code ARCHIVED}. Lives in {@code infrastructure/event}
 * because the Spring event-listener wiring is an infrastructure concern.
 *
 * <p>Listens for {@link KeyLifecycleEvent.ScheduledDeletion}, which is key-level: it is published
 * exactly once per cohort, so {@code ScheduleKeyDeletion} fires once for the cohort's shared key.
 * The event's {@code walletAlias} is the trigger alias and only labels the KMS audit row.
 *
 * <p>Exceptions are swallowed; operator visibility comes from {@code web3_treasury_kms_audits}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryWalletArchivedKmsHandler {

  private final ScheduleKmsKeyDeletionUseCase scheduleKmsKeyDeletionUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onKeyScheduledDeletion(KeyLifecycleEvent.ScheduledDeletion event) {
    try {
      scheduleKmsKeyDeletionUseCase.execute(
          new ScheduleKmsKeyDeletionCommand(
              event.walletAlias(),
              event.kmsKeyId(),
              event.walletAddress(),
              event.operatorUserId(),
              event.pendingWindowDays()));
    } catch (RuntimeException ex) {
      log.warn(
          "KMS scheduleKeyDeletion failed post-commit for kmsKeyId={} (trigger alias={});"
              + " audit row recorded for operator follow-up",
          event.kmsKeyId(),
          event.walletAlias(),
          ex);
    }
  }
}
