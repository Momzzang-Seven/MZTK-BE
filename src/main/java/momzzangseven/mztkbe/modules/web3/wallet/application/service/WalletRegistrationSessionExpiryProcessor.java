package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpireWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpiredWalletRegistrationSessionResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional processor that commits wallet session expiry before execution cancellation. */
@Service
@RequiredArgsConstructor
class WalletRegistrationSessionExpiryProcessor {

  private final LockWalletRegistrationSessionPort lockSessionPort;
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final Clock appClock;

  @Transactional
  public ExpiredWalletRegistrationSessionResult expire(
      ExpireWalletRegistrationSessionCommand command) {
    return lockSessionPort
        .lockByPublicIdForUpdate(command.registrationId())
        .map(this::expireIfNeeded)
        .orElseGet(ExpiredWalletRegistrationSessionResult::none);
  }

  private ExpiredWalletRegistrationSessionResult expireIfNeeded(WalletRegistrationSession session) {
    LocalDateTime now = LocalDateTime.now(appClock);
    if (!session.getStatus().isPreSubmissionExpirable()
        || session.getApprovalExpiresAt() == null
        || session.getApprovalExpiresAt().isAfter(now)) {
      return ExpiredWalletRegistrationSessionResult.none();
    }
    WalletRegistrationSession expired =
        session.expire(
            MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON,
            MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON,
            now);
    saveSessionPort.save(expired);
    return ExpiredWalletRegistrationSessionResult.expired(session.getLatestExecutionIntentId());
  }
}
