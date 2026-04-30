package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.service.TreasuryAuditRecorder;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletArchivedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletDisabledEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletProvisionedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler that writes the {@code success} business-flow audit row for treasury wallet
 * provisioning, disable and archive flows.
 *
 * <p>Previously each service body invoked {@code TreasuryAuditRecorder.record(..., true, null)}
 * inline. Because that recorder runs in {@code REQUIRES_NEW}, the inner audit transaction commits
 * independently of the outer service-level transaction. If the outer commit failed at the proxy
 * boundary the wallet row would silently roll back while the success audit row remained — leaving
 * "audit success / DB rolled back / KMS untouched" inconsistencies that operators could not detect.
 *
 * <p>Moving the success audit to {@code AFTER_COMMIT} guarantees the audit row only lands when the
 * wallet state transition has actually committed. Failure audits stay inside the service catch
 * blocks (still {@code REQUIRES_NEW}) so they survive an outer rollback, which is the intended
 * behaviour for "we tried and it blew up" records.
 *
 * <p>Each handler wraps the recorder call in {@code try/catch + log.warn} so an audit-side failure
 * cannot propagate up out of {@code afterCommit}. Spring invokes registered AFTER_COMMIT
 * synchronizations in a simple loop without per-listener exception isolation: an exception here
 * would surface as a 500 to the caller despite the wallet row already being committed, and would
 * skip subsequent AFTER_COMMIT listeners on the same event (e.g. KMS alias bind), leaving the
 * system in an unrecoverable half-bound state. Mirrors {@code TreasuryWalletProvisionedKmsHandler}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryAuditEventHandler {

  private final TreasuryAuditRecorder treasuryAuditRecorder;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProvisioned(TreasuryWalletProvisionedEvent event) {
    try {
      treasuryAuditRecorder.record(event.operatorUserId(), event.walletAddress(), true, null);
    } catch (RuntimeException ex) {
      log.warn(
          "Success audit failed post-commit for provisioned wallet (alias={}, operator={});"
              + " wallet row already committed, downstream AFTER_COMMIT handlers must still run",
          event.walletAlias(),
          event.operatorUserId(),
          ex);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDisabled(TreasuryWalletDisabledEvent event) {
    try {
      treasuryAuditRecorder.record(event.operatorUserId(), event.walletAddress(), true, null);
    } catch (RuntimeException ex) {
      log.warn(
          "Success audit failed post-commit for disabled wallet (alias={}, operator={});"
              + " wallet row already committed, downstream AFTER_COMMIT handlers must still run",
          event.walletAlias(),
          event.operatorUserId(),
          ex);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onArchived(TreasuryWalletArchivedEvent event) {
    try {
      treasuryAuditRecorder.record(event.operatorUserId(), event.walletAddress(), true, null);
    } catch (RuntimeException ex) {
      log.warn(
          "Success audit failed post-commit for archived wallet (alias={}, operator={});"
              + " wallet row already committed, downstream AFTER_COMMIT handlers must still run",
          event.walletAlias(),
          event.operatorUserId(),
          ex);
    }
  }
}
