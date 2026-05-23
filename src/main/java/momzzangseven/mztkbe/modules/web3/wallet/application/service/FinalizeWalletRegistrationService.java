package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.WalletRegistrationLocalConflictException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FinalizeWalletRegistrationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import org.springframework.stereotype.Service;

/** Finalizes local wallet registration after the approval execution is confirmed on-chain. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinalizeWalletRegistrationService implements FinalizeWalletRegistrationUseCase {

  private static final String SUPERSEDED_RETRY_INTENT_ERROR_CODE =
      "APPROVAL_SUPERSEDED_BY_CONFIRMED_RECEIPT";
  private static final String SUPERSEDED_RETRY_INTENT_ERROR_REASON =
      "approval retry superseded by confirmed receipt";

  private final WalletRegistrationFinalizationProcessor processor;
  private final WalletRegistrationFinalizationFailureRecorder failureRecorder;
  private final CancelWalletApprovalExecutionPort cancelExecutionPort;

  @Override
  public void execute(FinalizeWalletRegistrationCommand command) {
    try {
      WalletRegistrationFinalizationResult result = processor.finalizeConfirmed(command);
      cancelSupersededRetryIntent(result);
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

  private void cancelSupersededRetryIntent(WalletRegistrationFinalizationResult result) {
    if (result == null || !result.hasSupersededExecutionIntent()) {
      return;
    }
    try {
      cancelExecutionPort.cancelIfSignable(
          result.supersededExecutionIntentId(),
          SUPERSEDED_RETRY_INTENT_ERROR_CODE,
          SUPERSEDED_RETRY_INTENT_ERROR_REASON);
    } catch (RuntimeException exception) {
      log.error(
          "Failed to cancel superseded wallet registration retry intent: executionIntentId={}",
          result.supersededExecutionIntentId(),
          exception);
    }
  }
}
