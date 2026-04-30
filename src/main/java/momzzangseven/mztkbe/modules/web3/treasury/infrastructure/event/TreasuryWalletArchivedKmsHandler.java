package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ScheduleKmsKeyDeletionCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ScheduleKmsKeyDeletionUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletArchivedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler that drives the KMS {@code ScheduleKeyDeletion} side effect once the DB row
 * has transitioned to {@code ARCHIVED}. Lives in {@code infrastructure/event} because the Spring
 * event-listener wiring is an infrastructure concern.
 *
 * <p>Exceptions are swallowed; operator visibility comes from {@code web3_treasury_kms_audits}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryWalletArchivedKmsHandler {

  private final ScheduleKmsKeyDeletionUseCase scheduleKmsKeyDeletionUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onArchived(TreasuryWalletArchivedEvent event) {
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
          "KMS scheduleKeyDeletion failed post-commit for alias={}; audit row recorded for operator follow-up",
          event.walletAlias(),
          ex);
    }
  }
}
