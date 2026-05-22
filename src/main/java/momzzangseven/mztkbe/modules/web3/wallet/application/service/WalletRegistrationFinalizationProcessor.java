package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.WalletRegistrationLocalConflictException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.AcquireWalletRegistrationAuthorityLockPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.DeleteWalletAndFlushPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletAndFlushPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional processor for confirmed approval local wallet finalization. */
@Slf4j
@Service
@RequiredArgsConstructor
class WalletRegistrationFinalizationProcessor {

  static final String LOCAL_CONFLICT = "LOCAL_CONFLICT";

  private final LockWalletRegistrationSessionPort lockSessionPort;
  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final AcquireWalletRegistrationAuthorityLockPort authorityLockPort;
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final LoadWalletPort loadWalletPort;
  private final SaveWalletAndFlushPort saveWalletAndFlushPort;
  private final DeleteWalletAndFlushPort deleteWalletAndFlushPort;
  private final RecordWalletEventPort recordWalletEventPort;
  private final Clock appClock;

  @Transactional
  public void finalizeConfirmed(FinalizeWalletRegistrationCommand command) {
    WalletRegistrationSession authoritySnapshot =
        loadSessionPort
            .loadByPublicId(command.registrationId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "registrationId not found: " + command.registrationId()));
    authorityLockPort.lock(authoritySnapshot.getUserId(), authoritySnapshot.getWalletAddress());

    WalletRegistrationSession session =
        lockSessionPort
            .lockByPublicIdForUpdate(command.registrationId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "registrationId not found: " + command.registrationId()));

    boolean staleIntent = isStaleIntent(session, command.executionIntentId());
    boolean recoveredStaleIntent =
        staleIntent && canFinalizeRecoveredStaleIntent(session, command.executionIntentId());
    if (staleIntent && !recoveredStaleIntent) {
      log.info(
          "Skipping stale wallet finalization event: registrationId={}, sessionIntent={}, eventIntent={}",
          session.getPublicId(),
          session.getLatestExecutionIntentId(),
          command.executionIntentId());
      return;
    }
    if (session.getStatus() == WalletRegistrationStatus.REGISTERED) {
      return;
    }
    if (!recoveredStaleIntent && !isFinalizable(session)) {
      log.info(
          "Skipping wallet finalization from non-finalizable status: registrationId={}, status={}",
          session.getPublicId(),
          session.getStatus());
      return;
    }
    if (hasNewerAuthoritativeSession(session)) {
      log.info(
          "Skipping superseded wallet finalization event: registrationId={}, userId={}, walletAddress={}",
          session.getPublicId(),
          session.getUserId(),
          session.getWalletAddress());
      return;
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    WalletRegistrationSession confirmed =
        staleIntent
            ? session.markRecoveredApprovalConfirmed(command.executionIntentId(), "CONFIRMED", now)
            : session.markApprovalConfirmed(
                command.executionIntentId(), null, null, "CONFIRMED", now);

    UserWallet wallet = finalizeWallet(confirmed, command.executionIntentId());
    WalletRegistrationSession registered =
        confirmed.markRegistered(wallet.getId(), LocalDateTime.now(appClock));
    saveSessionPort.save(registered);
  }

  private boolean isStaleIntent(WalletRegistrationSession session, String executionIntentId) {
    return session.getLatestExecutionIntentId() == null
        || !session.getLatestExecutionIntentId().equals(executionIntentId);
  }

  private boolean canFinalizeRecoveredStaleIntent(
      WalletRegistrationSession session, String executionIntentId) {
    return session.hasReceiptTimeoutExecutionIntent(executionIntentId)
        && (session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
            || session.getStatus() == WalletRegistrationStatus.APPROVAL_SIGNED
            || session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
            || session.getStatus() == WalletRegistrationStatus.APPROVAL_RETRYABLE
            || session.getStatus() == WalletRegistrationStatus.APPROVAL_FAILED
            || session.getStatus().isConfirmedButNotFinalized());
  }

  private boolean isFinalizable(WalletRegistrationSession session) {
    return session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
        || session.getStatus() == WalletRegistrationStatus.APPROVAL_SIGNED
        || session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        || isReceiptTimeoutLateSuccess(session)
        || session.getStatus().isConfirmedButNotFinalized();
  }

  private boolean hasNewerAuthoritativeSession(WalletRegistrationSession session) {
    return session.getId() != null
        && session.getCreatedAt() != null
        && loadSessionPort.existsNewerByUserIdOrWalletAddress(
            session.getUserId(),
            session.getWalletAddress(),
            session.getCreatedAt(),
            session.getId());
  }

  private boolean isReceiptTimeoutLateSuccess(WalletRegistrationSession session) {
    return (session.getStatus() == WalletRegistrationStatus.APPROVAL_RETRYABLE
            || session.getStatus() == WalletRegistrationStatus.APPROVAL_FAILED)
        && WalletRegistrationReceiptTimeout.isRecordedOn(session);
  }

  private UserWallet finalizeWallet(WalletRegistrationSession session, String executionIntentId) {
    List<UserWallet> activeUserWallets =
        loadWalletPort.findWalletsByUserIdAndStatus(session.getUserId(), WalletStatus.ACTIVE);
    if (activeUserWallets.size() == 1) {
      UserWallet activeWallet = activeUserWallets.get(0);
      if (activeWallet.getWalletAddress().equals(session.getWalletAddress())) {
        return activeWallet;
      }
    }
    if (!activeUserWallets.isEmpty()) {
      throw localConflict("user already has an active wallet");
    }

    return loadWalletPort
        .findByWalletAddress(session.getWalletAddress())
        .map(existing -> finalizeExistingWallet(session, existing, executionIntentId))
        .orElseGet(() -> registerNewWallet(session, executionIntentId));
  }

  private UserWallet finalizeExistingWallet(
      WalletRegistrationSession session, UserWallet existingWallet, String executionIntentId) {
    if (existingWallet.getStatus() == WalletStatus.ACTIVE) {
      if (existingWallet.belongsTo(session.getUserId())) {
        return existingWallet;
      }
      throw localConflict("wallet address already has an active owner");
    }
    if (existingWallet.getStatus() == WalletStatus.BLOCKED) {
      throw localConflict("wallet address is blocked");
    }
    if (!existingWallet.canBeReRegistered()) {
      throw localConflict(
          "wallet address cannot be re-registered from " + existingWallet.getStatus());
    }

    Long previousUserId = existingWallet.getUserId();
    WalletStatus previousStatus = existingWallet.getStatus();
    deleteWalletAndFlushPort.deleteByIdAndFlush(existingWallet.getId());
    recordWalletEventPort.record(
        WalletEvent.hardDeleted(
            existingWallet.getWalletAddress(),
            previousUserId,
            previousStatus,
            Map.of(
                "source",
                "application",
                "action",
                "re_registration_cleanup",
                "registration_id",
                session.getPublicId(),
                "execution_intent_id",
                executionIntentId,
                "new_user_id",
                session.getUserId())));

    UserWallet savedWallet =
        saveWalletAndFlushPort.saveAndFlush(
            UserWallet.create(
                session.getUserId(), session.getWalletAddress(), Instant.now(appClock)));
    recordWalletEventPort.record(
        WalletEvent.reRegistered(
            savedWallet.getWalletAddress(),
            savedWallet.getUserId(),
            previousUserId,
            previousStatus,
            Map.of(
                "source",
                "application",
                "action",
                "re_registration",
                "registration_id",
                session.getPublicId(),
                "execution_intent_id",
                executionIntentId,
                "previous_user_id",
                previousUserId,
                "previous_status",
                previousStatus.name())));
    return savedWallet;
  }

  private UserWallet registerNewWallet(
      WalletRegistrationSession session, String executionIntentId) {
    UserWallet savedWallet =
        saveWalletAndFlushPort.saveAndFlush(
            UserWallet.create(
                session.getUserId(), session.getWalletAddress(), Instant.now(appClock)));
    recordWalletEventPort.record(
        WalletEvent.registered(
            savedWallet.getWalletAddress(),
            savedWallet.getUserId(),
            Map.of(
                "source",
                "application",
                "action",
                "new_registration",
                "registration_id",
                session.getPublicId(),
                "execution_intent_id",
                executionIntentId)));
    return savedWallet;
  }

  private WalletRegistrationLocalConflictException localConflict(String reason) {
    return new WalletRegistrationLocalConflictException(LOCAL_CONFLICT, reason);
  }
}
