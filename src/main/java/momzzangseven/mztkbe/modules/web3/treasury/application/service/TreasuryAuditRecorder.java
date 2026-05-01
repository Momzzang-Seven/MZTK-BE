package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>Audit failures are intentionally swallowed so a logging or DB hiccup on the audit path can
 * never propagate up and mask the original (already failing) business error.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryAuditRecorder {

  private final RecordTreasuryProvisionAuditPort recordTreasuryProvisionAuditPort;

  /**
   * Persist a single audit row in its own committed transaction.
   *
   * @param operatorId admin user id invoking the operation
   * @param walletAddress {@code 0x}-prefixed wallet address, or {@code null} when the failure
   *     happened before the address could be derived
   * @param success {@code true} for successful flows, {@code false} for caught exceptions
   * @param failureReason simple class name of the thrown exception, or {@code null} on success
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(Long operatorId, String walletAddress, boolean success, String failureReason) {
    try {
      recordTreasuryProvisionAuditPort.record(
          new RecordTreasuryProvisionAuditPort.AuditCommand(
              operatorId, walletAddress, success, failureReason));
    } catch (Exception e) {
      log.warn(
          "Failed to record treasury audit: operatorId={}, success={}", operatorId, success, e);
    }
  }
}
