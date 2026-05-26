package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.springframework.stereotype.Component;

/** Persists and logs the wallet-registration receipt-timeout operator review state. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletRegistrationReceiptTimeoutMarker {

  private final SaveWalletRegistrationSessionPort saveSessionPort;

  public WalletRegistrationSession markSponsorNonceBlocked(
      WalletRegistrationSession session, LocalDateTime now) {
    WalletRegistrationSession updated =
        session.markSponsorNonceBlocked(
            WalletRegistrationReceiptTimeout.ERROR_CODE,
            WalletRegistrationReceiptTimeout.ERROR_REASON,
            now);
    log.warn(
        "wallet registration sponsor nonce blocked: registrationId={}, walletAddress={}, "
            + "latestExecutionIntentId={}, errorCode={}",
        updated.getPublicId(),
        updated.getWalletAddress(),
        updated.getLatestExecutionIntentId(),
        WalletRegistrationReceiptTimeout.ERROR_CODE);
    return saveSessionPort.save(updated);
  }
}
