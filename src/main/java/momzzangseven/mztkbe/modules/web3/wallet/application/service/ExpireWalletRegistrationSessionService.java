package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpireWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpiredWalletRegistrationSessionResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ExpireWalletRegistrationSessionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import org.springframework.stereotype.Service;

/** Expires wallet registration sessions and then cancels signable execution intents best-effort. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpireWalletRegistrationSessionService
    implements ExpireWalletRegistrationSessionUseCase {

  private final WalletRegistrationSessionExpiryProcessor expiryProcessor;
  private final CancelWalletApprovalExecutionPort cancelExecutionPort;

  @Override
  public boolean execute(ExpireWalletRegistrationSessionCommand command) {
    ExpiredWalletRegistrationSessionResult result = expiryProcessor.expire(command);
    if (!result.expired() || result.canceledExecutionIntentId() == null) {
      return result.expired();
    }
    cancelSignableIntent(result.canceledExecutionIntentId());
    return true;
  }

  private void cancelSignableIntent(String executionIntentId) {
    try {
      cancelExecutionPort.cancelIfSignable(
          executionIntentId,
          MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON,
          MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON);
    } catch (RuntimeException exception) {
      log.warn(
          "Failed to cancel expired wallet registration approval intent: executionIntentId={}",
          executionIntentId,
          exception);
    }
  }
}
