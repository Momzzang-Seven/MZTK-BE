package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.RecordTreasuryAuditUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.RecordTreasuryProvisionAuditPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Standalone bean that wraps {@link RecordTreasuryProvisionAuditPort#record} in a {@link
 * Propagation#REQUIRES_NEW} transaction.
 *
 * <p>Extracted from the provisioning / disable / archive services because Spring AOP cannot
 * intercept self-invoked methods — calling {@code this.recordAudit(...)} from inside the same
 * service bypasses the {@code @Transactional} proxy and silently runs in the caller's transaction,
 * which means audit rows would roll back together with a failing outer transaction. Routing audit
 * writes through this dedicated bean restores the {@code REQUIRES_NEW} guarantee so audit rows
 * survive even when KMS-side calls throw and cause the outer transaction to roll back.
 *
 * <p>Implements {@link RecordTreasuryAuditUseCase} so {@code infrastructure/event} handlers depend
 * on the input port rather than this concrete class (ARCHITECTURE.md layering rule).
 *
 * <p>Audit failures are intentionally swallowed so a logging or DB hiccup on the audit path can
 * never propagate up and mask the original (already failing) business error.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryAuditRecorder implements RecordTreasuryAuditUseCase {

  private final RecordTreasuryProvisionAuditPort recordTreasuryProvisionAuditPort;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long operatorUserId,
      String walletAlias,
      String walletAddress,
      boolean success,
      String reason) {
    try {
      recordTreasuryProvisionAuditPort.record(
          new RecordTreasuryProvisionAuditPort.AuditCommand(
              operatorUserId, walletAlias, walletAddress, success, reason));
    } catch (Exception e) {
      log.warn(
          "Failed to record treasury audit: operatorId={}, alias={}, success={}",
          operatorUserId,
          walletAlias,
          success,
          e);
    }
  }
}
