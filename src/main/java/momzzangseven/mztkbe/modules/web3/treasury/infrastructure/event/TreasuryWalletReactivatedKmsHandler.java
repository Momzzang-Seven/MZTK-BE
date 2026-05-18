package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.EnableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.EnableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletReactivatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler for the ReEnableSameKey action (MOM-444 C5). Lives in {@code
 * infrastructure/event} because Spring's event-listener wiring is an infrastructure concern; the
 * KMS enableKey call and audit row are driven by {@link EnableKmsKeyUseCase} so the application
 * layer owns port orchestration, mirroring {@link TreasuryWalletDisabledKmsHandler}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryWalletReactivatedKmsHandler {

  private final EnableKmsKeyUseCase enableKmsKeyUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(TreasuryWalletReactivatedEvent event) {
    try {
      enableKmsKeyUseCase.execute(
          new EnableKmsKeyCommand(
              event.walletAlias(),
              event.kmsKeyId(),
              event.walletAddress(),
              event.operatorUserId()));
    } catch (RuntimeException ex) {
      log.warn(
          "KMS enableKey failed post-commit for alias={}; audit row recorded for operator follow-up",
          event.walletAlias(),
          ex);
    }
  }
}
