package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletDisabledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler that drives the KMS {@code DisableKey} side effect once the DB row has
 * transitioned to {@code DISABLED}. Lives in {@code infrastructure/event} because the Spring
 * event-listener wiring is an infrastructure concern; the application-layer use case it delegates
 * to is unaware of how it is invoked.
 *
 * <p>Exceptions thrown from the use case are logged and swallowed here. The
 * {@code TransactionSynchronization.afterCommit} contract already swallows listener exceptions, so
 * letting them escape would only litter logs without giving the API caller anything actionable.
 * Operators see KMS failures via {@code web3_treasury_kms_audits} (written inside the use case
 * with {@code REQUIRES_NEW}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryWalletDisabledKmsHandler {

  private final DisableKmsKeyUseCase disableKmsKeyUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDisabled(TreasuryWalletDisabledEvent event) {
    try {
      disableKmsKeyUseCase.execute(
          new DisableKmsKeyCommand(
              event.walletAlias(),
              event.kmsKeyId(),
              event.walletAddress(),
              event.operatorUserId()));
    } catch (RuntimeException ex) {
      log.warn(
          "KMS disableKey failed post-commit for alias={}; audit row recorded for operator follow-up",
          event.walletAlias(),
          ex);
    }
  }
}
