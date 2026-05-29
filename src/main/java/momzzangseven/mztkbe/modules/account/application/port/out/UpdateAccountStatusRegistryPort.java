package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.Map;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/**
 * Write side of the in-memory account-status denylist (negative cache).
 *
 * <p>Updates are applied in-memory only and acquire zero database connections. Incremental
 * mutations ({@link #put} / {@link #evict}) are driven by status-change events; {@link #replaceAll}
 * is driven by reconcile/warmup to re-synchronize the denylist against a fresh DB snapshot.
 */
public interface UpdateAccountStatusRegistryPort {

  /**
   * Records a non-ACTIVE user in the denylist (or overwrites its status). Used when a user
   * transitions to BLOCKED / DELETED / UNVERIFIED.
   *
   * @param userId the user identifier
   * @param status the non-ACTIVE status to record
   */
  void put(Long userId, AccountStatus status);

  /**
   * Removes a user from the denylist. Used when a user becomes ACTIVE again (recovery) or is
   * hard-deleted. Removing an absent userId is a no-op (idempotent).
   *
   * @param userId the user identifier to remove
   */
  void evict(Long userId);

  /**
   * Atomically replaces the entire denylist with a fresh snapshot. Entries absent from the snapshot
   * are dropped; entries present are installed. Used by reconcile/warmup to rebuild the denylist
   * from the authoritative DB state.
   *
   * @param snapshot the new full denylist contents (userId → non-ACTIVE status); a {@code null}
   *     snapshot is treated as empty
   */
  void replaceAll(Map<Long, AccountStatus> snapshot);
}
