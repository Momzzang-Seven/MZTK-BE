package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.config.WalletHardDeleteProperties;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.DeleteWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wallet hard delete service
 *
 * <p>Handles two types of wallet hard deletion:
 *
 * <ul>
 *   <li>Scheduled deletion: UNLINKED wallets after retention period
 *   <li>Cascade deletion: USER_DELETED wallets when users are hard-deleted
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletHardDeleteService {

  private final LoadWalletPort loadWalletPort;
  private final DeleteWalletPort deleteWalletPort;
  private final RecordWalletEventPort recordWalletEventPort;
  private final WalletHardDeleteProperties props;

  /**
   * Run one hard-delete batch for UNLINKED wallets
   *
   * <p>Only deletes UNLINKED wallets (user-initiated unlinking). USER_DELETED wallets are handled
   * by WithdrawalHardDeleteService.
   *
   * @param now current timestamp
   * @return number of deleted wallets in this batch
   */
  @Transactional
  public int runBatch(Instant now) {
    int retentionDays = props.getRetentionDays();
    if (retentionDays <= 0) {
      throw new IllegalArgumentException("retentionDays must be greater than 0");
    }

    int batchSize = props.getBatchSize();
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be greater than 0");
    }

    Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);

    // Load UNLINKED wallets only (USER_DELETED handled by User module)
    List<LoadWalletPort.WalletDeletionInfo> wallets =
        loadWalletPort.loadWalletsForDeletion(cutoff, batchSize);
    if (wallets.isEmpty()) {
      return 0;
    }

    // record event
    recordEvent(wallets, retentionDays, WalletStatus.UNLINKED, "scheduled_cleanup");

    // Batch hard-deletion
    List<Long> walletIds = wallets.stream().map(LoadWalletPort.WalletDeletionInfo::id).toList();
    deleteWalletPort.deleteAllByIdInBatch(walletIds);

    log.info(
        "Hard deleted UNLINKED wallets: count={}, cutoff={}, retentionDays={}",
        wallets.size(),
        cutoff,
        retentionDays);

    return wallets.size();
  }

  /**
   * Delete USER_DELETED wallets by user IDs (cascade delete)
   *
   * <p>Called by UserHardDeleteEventHandler when it receives user hard-delete event. This ensures
   * wallets are deleted before users, maintaining FK constraints.
   *
   * @param userIds user IDs to delete wallets for
   * @return number of deleted wallets
   */
  @Transactional
  public int deleteByUserIds(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return 0;
    }

    // Load USER_DELETED wallets for given users
    List<LoadWalletPort.WalletDeletionInfo> wallets =
        loadWalletPort.findWalletsByUserIdAndUserDeleted(userIds);

    if (wallets.isEmpty()) {
      log.info("No USER_DELETED wallets found for users: count={}", userIds.size());
      return 0;
    }

    // Record event
    recordEvent(
        wallets,
        props.getRetentionDays(),
        WalletStatus.USER_DELETED,
        "user_hard_delete_cascade");

    // Batch hard-deletion
    List<Long> walletIds = wallets.stream().map(LoadWalletPort.WalletDeletionInfo::id).toList();
    deleteWalletPort.deleteAllByIdInBatch(walletIds);

    log.info(
        "Deleted USER_DELETED wallets for users: walletCount={}, userCount={}",
        wallets.size(),
        userIds.size());
    return wallets.size();
  }

  private void recordEvent(
      List<LoadWalletPort.WalletDeletionInfo> wallets,
      int retentionDays,
      WalletStatus previousStatus,
      String action) {
    // Record event
    List<WalletEvent> events =
        wallets.stream()
            .map(
                w ->
                    WalletEvent.hardDeleted(
                        w.walletAddress(),
                        w.userId(),
                        previousStatus,
                        Map.of(
                            "source",
                            "scheduler",
                            "action",
                            action,
                            "retention_days",
                            retentionDays)))
            .toList();

    recordWalletEventPort.recordBatch(events);
  }
}
