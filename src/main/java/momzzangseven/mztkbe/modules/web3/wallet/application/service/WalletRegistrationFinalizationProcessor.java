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
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.WalletRegistrationLocalConflictException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.DeleteWalletAndFlushPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
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
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final LoadWalletPort loadWalletPort;
  private final SaveWalletAndFlushPort saveWalletAndFlushPort;
  private final DeleteWalletAndFlushPort deleteWalletAndFlushPort;
  private final RecordWalletEventPort recordWalletEventPort;
  private final Clock appClock;

  @Transactional
  public void finalizeConfirmed(FinalizeWalletRegistrationCommand command) {
    WalletRegistrationSession session =
        lockSessionPort
            .lockByPublicIdForUpdate(command.registrationId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "registrationId not found: " + command.registrationId()));

    if (isStaleIntent(session, command.executionIntentId())) {
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
    if (!isFinalizable(session)) {
      log.info(
          "Skipping wallet finalization from non-finalizable status: registrationId={}, status={}",
          session.getPublicId(),
          session.getStatus());
      return;
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    WalletRegistrationSession confirmed =
        session.markApprovalConfirmed(command.executionIntentId(), null, null, "CONFIRMED", now);

    UserWallet wallet = finalizeWallet(confirmed, command.executionIntentId());
    WalletRegistrationSession registered =
        confirmed.markRegistered(wallet.getId(), LocalDateTime.now(appClock));
    saveSessionPort.save(registered);
  }

  private boolean isStaleIntent(WalletRegistrationSession session, String executionIntentId) {
    return session.getLatestExecutionIntentId() == null
        || !session.getLatestExecutionIntentId().equals(executionIntentId);
  }

  private boolean isFinalizable(WalletRegistrationSession session) {
    return session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
        || session.getStatus() == WalletRegistrationStatus.APPROVAL_SIGNED
        || session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        || session.getStatus().isConfirmedButNotFinalized();
  }

  private UserWallet finalizeWallet(WalletRegistrationSession session, String executionIntentId) {
    List<UserWallet> activeUserWallets =
        loadWalletPort.findWalletsByUserIdAndStatus(session.getUserId(), WalletStatus.ACTIVE);
    for (UserWallet activeWallet : activeUserWallets) {
      if (activeWallet.getWalletAddress().equals(session.getWalletAddress())) {
        return activeWallet;
      }
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
