package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsAuditPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Standalone bean wrapping {@link KmsAuditPort#record} in a {@link Propagation#REQUIRES_NEW}
 * transaction so KMS-side outcome rows survive caller-side rollbacks. Mirrors the {@code
 * TreasuryAuditRecorder} pattern — the separate bean is required so Spring AOP can intercept the
 * call (a self-invoked transactional method would silently lose its propagation hint).
 *
 * <p>Audit failures are intentionally swallowed; a logging or DB hiccup on the audit path must
 * never propagate up and mask the original (already failing) KMS error.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KmsAuditRecorder {

  private final KmsAuditPort kmsAuditPort;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long operatorId,
      String walletAlias,
      String kmsKeyId,
      String walletAddress,
      KmsAuditAction action,
      boolean success,
      String failureReason) {
    try {
      kmsAuditPort.record(
          new KmsAuditPort.AuditCommand(
              operatorId, walletAlias, kmsKeyId, walletAddress, action, success, failureReason));
    } catch (Exception e) {
      log.warn(
          "Failed to record KMS audit: alias={}, action={}, success={}",
          walletAlias,
          action,
          success,
          e);
    }
  }
}
