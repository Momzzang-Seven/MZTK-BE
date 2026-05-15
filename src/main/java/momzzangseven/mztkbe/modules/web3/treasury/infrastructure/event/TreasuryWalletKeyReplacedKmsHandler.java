package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ReplaceKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletKeyReplacedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler for the ReplaceKey action (MOM-444 C6–C9). Lives in {@code
 * infrastructure/event} because Spring's event-listener wiring is an infrastructure concern; the
 * KMS orchestration itself runs inside {@link ReplaceKmsKeyUseCase} so the application layer drives
 * port calls and audit writes, mirroring {@link TreasuryWalletDisabledKmsHandler}.
 *
 * <p>Exceptions from the use case are logged and swallowed. {@code
 * TransactionSynchronization.afterCommit} already swallows listener exceptions, so propagating
 * would only litter logs; operators see KMS failures via {@code web3_treasury_kms_audits} (written
 * inside the use case with {@code REQUIRES_NEW}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryWalletKeyReplacedKmsHandler {

  private final ReplaceKmsKeyUseCase replaceKmsKeyUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(TreasuryWalletKeyReplacedEvent event) {
    try {
      replaceKmsKeyUseCase.execute(
          new ReplaceKmsKeyCommand(
              event.walletAlias(),
              event.oldKmsKeyId(),
              event.newKmsKeyId(),
              event.walletAddress(),
              event.operatorUserId(),
              event.disposeOldKey()));
    } catch (RuntimeException ex) {
      log.warn(
          "KMS ReplaceKey failed post-commit for alias={}; audit rows recorded for operator follow-up",
          event.walletAlias(),
          ex);
    }
  }
}
