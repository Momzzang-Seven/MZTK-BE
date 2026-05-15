package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.service.KmsAuditRecorder;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletReactivatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler for the ReEnableSameKey action (MOM-444 C5). Calls {@code
 * KmsKeyLifecyclePort#enableKey} on the existing KMS key. Failures land in {@code
 * web3_treasury_kms_audits} as a {@code KMS_ENABLE} failure row.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryWalletReactivatedKmsHandler {

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(TreasuryWalletReactivatedEvent event) {
    try {
      kmsKeyLifecyclePort.enableKey(event.kmsKeyId());
      kmsAuditRecorder.record(
          event.operatorUserId(),
          event.walletAlias(),
          event.kmsKeyId(),
          event.walletAddress(),
          KmsAuditAction.KMS_ENABLE,
          true,
          null);
    } catch (RuntimeException ex) {
      log.warn(
          "KMS enableKey failed post-commit for alias={} (kmsKeyId={}); audit row recorded",
          event.walletAlias(),
          event.kmsKeyId(),
          ex);
      kmsAuditRecorder.record(
          event.operatorUserId(),
          event.walletAlias(),
          event.kmsKeyId(),
          event.walletAddress(),
          KmsAuditAction.KMS_ENABLE,
          false,
          ex.getClass().getSimpleName());
    }
  }
}
