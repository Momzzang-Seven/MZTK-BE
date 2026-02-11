package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.user.domain.event.UsersHardDeletedEvent;
import momzzangseven.mztkbe.modules.web3.wallet.application.service.WalletHardDeleteService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for user hard deletion
 *
 * <p>Listens to UsersHardDeletedEvent and cascade-deletes USER_DELETED wallets associated with the
 * deleted users.
 *
 * <p>Uses REQUIRES_NEW propagation to ensure wallet deletion happens in a separate transaction from
 * user deletion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletUserHardDeleteEventHandler {

  private final WalletHardDeleteService walletHardDeleteService;

  /**
   * Handle users hard deleted event
   *
   * @param event event containing user IDs that were hard-deleted
   */
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleUsersHardDeleted(UsersHardDeletedEvent event) {
    List<Long> userIds = event.userIds();

    if (userIds == null || userIds.isEmpty()) {
      log.warn("Received UsersHardDeletedEvent with empty userIds");
      return;
    }

    log.info(
        "Handling user hard delete event: cascade deleting USER_DELETED wallets for userCount={}",
        userIds.size());

    try {
      int deletedCount = walletHardDeleteService.deleteByUserIds(userIds);

      log.info(
          "Successfully cascade deleted USER_DELETED wallets: walletCount={}, userCount={}",
          deletedCount,
          userIds.size());
    } catch (Exception e) {
      log.error(
          "Failed to cascade delete USER_DELETED wallets for users: userCount={}",
          userIds.size(),
          e);
      // Don't throw - let other event listeners continue
      // The wallet scheduler will eventually clean them up
    }
  }
}
