package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.GetWalletRegistrationStatusQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetWalletRegistrationStatusUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-bound wallet registration status service. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetWalletRegistrationStatusService implements GetWalletRegistrationStatusUseCase {

  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final Clock appClock;

  @Override
  @Transactional
  public WalletRegistrationStatusResult execute(GetWalletRegistrationStatusQuery query) {
    WalletRegistrationSession session =
        loadSessionPort
            .loadByPublicIdAndUserId(query.registrationId(), query.requesterUserId())
            .orElseThrow(WalletNotFoundException::new);
    Optional<WalletApprovalExecutionStateView> executionState = loadExecutionState(session);
    LocalDateTime now = LocalDateTime.now(appClock);
    WalletRegistrationSession visibleSession =
        markSponsorNonceBlockedIfReceiptTimeout(session, executionState, now);
    return WalletRegistrationStatusResult.from(visibleSession, executionState.orElse(null), now);
  }

  private Optional<WalletApprovalExecutionStateView> loadExecutionState(
      WalletRegistrationSession session) {
    if (session.getLatestExecutionIntentId() == null) {
      return Optional.empty();
    }
    return loadExecutionStatePort.loadByExecutionIntentId(
        session.getUserId(), session.getLatestExecutionIntentId());
  }

  private WalletRegistrationSession markSponsorNonceBlockedIfReceiptTimeout(
      WalletRegistrationSession session,
      Optional<WalletApprovalExecutionStateView> executionState,
      LocalDateTime now) {
    if (session.getStatus() != WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        || executionState.filter(WalletRegistrationReceiptTimeout::isCurrent).isEmpty()) {
      return session;
    }

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
