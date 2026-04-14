package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.domain.event.UsersHardDeletedEvent;
import momzzangseven.mztkbe.modules.web3.wallet.application.service.WalletHardDeleteService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for user hard deletion.
 *
 * <p>Listens to {@link UsersHardDeletedEvent} and cascade-deletes USER_DELETED wallets associated
 * with the deleted users.
 *
 * <p>Uses {@code REQUIRED} propagation to participate in the same transaction as the hard-delete
 * batch. This ensures wallet deletion occurs before the {@code users} row is removed, satisfying
 * the FK constraint {@code user_wallets.user_id REFERENCES users(id)}. If wallet deletion fails,
 * the entire transaction rolls back and the scheduler retries the batch the next day.
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
  @Transactional
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
          "CRITICAL: Failed to delete USER_DELETED wallets. User deletion will be rolled back"
              + " and retried tomorrow. userCount={}",
          userIds.size(),
          e);
      throw e;
    }
  }
}
