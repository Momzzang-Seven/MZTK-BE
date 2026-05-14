package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.RecordTreasuryAuditUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasArchivedAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasDisabledAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasProvisionedAuditEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler that writes the {@code success} business-flow audit row for treasury wallet
 * provisioning, disable and archive flows.
 *
 * <p>Audit is alias-level: for a cohort of N sibling aliases the service publishes N alias events,
 * so this handler writes N audit rows — one per alias. The KMS-level audit (one per cohort) is
 * written separately inside the KMS use cases.
 *
 * <p>The success audit runs in {@code AFTER_COMMIT} so the audit row only lands when the wallet
 * state transition has actually committed. Failure audits stay inside the service catch blocks
 * (still {@code REQUIRES_NEW}) so they survive an outer rollback.
 *
 * <p>Depends on {@link RecordTreasuryAuditUseCase} (a {@code port/in}) rather than the concrete
 * {@code TreasuryAuditRecorder} — event handlers are driving adapters and must not import {@code
 * application/service} directly (ARCHITECTURE.md).
 *
 * <p>Each handler wraps the recorder call in {@code try/catch + log.warn} so an audit-side failure
 * cannot propagate out of {@code afterCommit}. Spring invokes registered AFTER_COMMIT
 * synchronizations in a simple loop without per-listener exception isolation: an exception here
 * would surface as a 500 despite the wallet rows already being committed, and would skip subsequent
 * AFTER_COMMIT listeners on the same event (e.g. KMS alias bind).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryAuditEventHandler {

  private final RecordTreasuryAuditUseCase recordTreasuryAuditUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProvisioned(AliasProvisionedAuditEvent event) {
    try {
      recordTreasuryAuditUseCase.record(
          event.operatorUserId(), event.walletAlias(), event.walletAddress(), true, null);
    } catch (RuntimeException ex) {
      log.warn(
          "Success audit failed post-commit for provisioned alias={} (operator={});"
              + " wallet row already committed, downstream AFTER_COMMIT handlers must still run",
          event.walletAlias(),
          event.operatorUserId(),
          ex);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDisabled(AliasDisabledAuditEvent event) {
    try {
      recordTreasuryAuditUseCase.record(
          event.operatorUserId(), event.walletAlias(), event.walletAddress(), true, null);
    } catch (RuntimeException ex) {
      log.warn(
          "Success audit failed post-commit for disabled alias={} (operator={});"
              + " wallet row already committed, downstream AFTER_COMMIT handlers must still run",
          event.walletAlias(),
          event.operatorUserId(),
          ex);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onArchived(AliasArchivedAuditEvent event) {
    try {
      recordTreasuryAuditUseCase.record(
          event.operatorUserId(), event.walletAlias(), event.walletAddress(), true, null);
    } catch (RuntimeException ex) {
      log.warn(
          "Success audit failed post-commit for archived alias={} (operator={});"
              + " wallet row already committed, downstream AFTER_COMMIT handlers must still run",
          event.walletAlias(),
          event.operatorUserId(),
          ex);
    }
  }
}
