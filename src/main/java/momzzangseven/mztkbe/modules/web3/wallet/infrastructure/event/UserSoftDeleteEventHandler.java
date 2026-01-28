package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.event;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.user.domain.event.UserSoftDeletedEvent;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for user soft-deletion
 *
 * <p>Handles user withdrawal by marking associated wallet as USER_DELETED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSoftDeleteEventHandler {

  private final LoadWalletPort loadWalletPort;
  private final SaveWalletPort saveWalletPort;
  private final RecordWalletEventPort recordWalletEventPort;

  /**
   * Handle user soft-deleted event performs: update wallet status to USER_DELETED, and record the
   * event
   *
   * @param event user deleted event
   */
  @EventListener
  @Transactional
  public void handleUserSoftDeleted(UserSoftDeletedEvent event) {
    Long userId = event.userId();
    if (userId == null) {
      log.warn("Received UserSoftDeletedEvent with 0 userId");
      return;
    }

    log.info("Handling user deleted event: userId={}", userId);

    // Find user's ACTIVE wallet.
    List<UserWallet> wallets =
        loadWalletPort.findWalletsByUserIdAndStatus(event.userId(), WalletStatus.ACTIVE);

    if (wallets.isEmpty()) {
      // User doesn't have wallet
      log.info("No wallets found for userId={}", userId);
      return;
    }

    // Get user wallet, mark as USER_DELETED, and save it.
    UserWallet userWallet = wallets.get(0);
    userWallet.markAsUserDeleted();
    saveWalletPort.save(userWallet);

    // Record event
    recordWalletEventPort.record(
        WalletEvent.userDeleted(
            userWallet.getWalletAddress(),
            userWallet.getUserId(),
            Map.of(
                "source", "event_handler",
                "action", "user_withdrawal",
                "user_id", userWallet.getUserId())));

    log.info(
        "Marked wallet as USER_DELETED: walletId={}, userId={}, address={}",
        userWallet.getId(),
        userWallet.getUserId(),
        userWallet.getWalletAddress());
  }
}
