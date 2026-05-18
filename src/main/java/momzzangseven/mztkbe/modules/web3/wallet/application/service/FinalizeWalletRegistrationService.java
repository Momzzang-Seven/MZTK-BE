package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.WalletRegistrationLocalConflictException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FinalizeWalletRegistrationUseCase;
import org.springframework.stereotype.Service;

/** Finalizes local wallet registration after the approval execution is confirmed on-chain. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinalizeWalletRegistrationService implements FinalizeWalletRegistrationUseCase {

  private final WalletRegistrationFinalizationProcessor processor;
  private final WalletRegistrationFinalizationFailureRecorder failureRecorder;

  @Override
  public void execute(FinalizeWalletRegistrationCommand command) {
    try {
      processor.finalizeConfirmed(command);
    } catch (WalletRegistrationLocalConflictException exception) {
      failureRecorder.recordLocalConflict(command, exception.errorCode(), exception.getMessage());
    } catch (RuntimeException exception) {
      log.error(
          "Wallet registration finalization failed: registrationId={}, executionIntentId={}",
          command.registrationId(),
          command.executionIntentId(),
          exception);
      failureRecorder.recordUnexpectedFailure(
          command,
          WalletRegistrationFinalizationFailureRecorder.FINALIZATION_FAILED,
          safeMessage(exception));
    }
  }

  private String safeMessage(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }
}
