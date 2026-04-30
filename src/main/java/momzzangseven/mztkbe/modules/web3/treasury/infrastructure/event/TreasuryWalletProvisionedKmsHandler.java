package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.BindKmsAliasCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.BindKmsAliasUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletProvisionedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler that drives KMS {@code CreateAlias} (or {@code UpdateAlias} for ghost
 * recovery) once the {@code kms_key_id} has been persisted. Lives in {@code infrastructure/event}
 * so the application layer remains unaware of Spring's event-listener wiring.
 *
 * <p>Exceptions are swallowed; operator visibility comes from {@code web3_treasury_kms_audits}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryWalletProvisionedKmsHandler {

  private final BindKmsAliasUseCase bindKmsAliasUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProvisioned(TreasuryWalletProvisionedEvent event) {
    try {
      bindKmsAliasUseCase.execute(
          new BindKmsAliasCommand(
              event.walletAlias(),
              event.kmsKeyId(),
              event.walletAddress(),
              event.operatorUserId()));
    } catch (RuntimeException ex) {
      log.warn(
          "KMS alias bind failed post-commit for alias={} (repairMode={}); audit row recorded for operator follow-up",
          event.walletAlias(),
          event.aliasRepairMode(),
          ex);
    }
  }
}
