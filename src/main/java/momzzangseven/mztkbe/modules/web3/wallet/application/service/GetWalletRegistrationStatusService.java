package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.GetWalletRegistrationStatusQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetWalletRegistrationStatusUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-bound wallet registration status service. */
@Service
@RequiredArgsConstructor
public class GetWalletRegistrationStatusService implements GetWalletRegistrationStatusUseCase {

  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final LockWalletRegistrationSessionPort lockSessionPort;
  private final LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  private final WalletRegistrationReceiptTimeoutMarker receiptTimeoutMarker;
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
    if (!isReceiptTimeout(session, executionState)) {
      return WalletRegistrationStatusResult.from(session, executionState.orElse(null), now);
    }

    WalletRegistrationSession lockedSession =
        lockSessionPort
            .lockByPublicIdForUpdate(query.registrationId())
            .orElseThrow(WalletNotFoundException::new);
    if (!lockedSession.getUserId().equals(query.requesterUserId())) {
      throw new WalletNotFoundException();
    }

    Optional<WalletApprovalExecutionStateView> lockedExecutionState =
        loadExecutionState(lockedSession);
    WalletRegistrationSession visibleSession =
        markSponsorNonceBlockedIfReceiptTimeout(lockedSession, lockedExecutionState, now);
    return WalletRegistrationStatusResult.from(
        visibleSession, lockedExecutionState.orElse(null), now);
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
    if (!isReceiptTimeout(session, executionState)) {
      return session;
    }
    return receiptTimeoutMarker.markSponsorNonceBlocked(session, now);
  }

  private boolean isReceiptTimeout(
      WalletRegistrationSession session,
      Optional<WalletApprovalExecutionStateView> executionState) {
    return session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        && executionState.filter(WalletRegistrationReceiptTimeout::isCurrent).isPresent();
  }
}
