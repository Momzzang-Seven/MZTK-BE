package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.Map;
import java.util.function.Supplier;
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
   * <p>The {@code snapshotLoader} is invoked <strong>while the registry holds its write
   * lock</strong> so that loading the snapshot (a DB read) and swapping it in are atomic with
   * respect to the incremental {@link #put} / {@link #evict} mutations. This closes the reconcile
   * race where an event landing between "snapshot read" and "swap" would be clobbered by a stale
   * snapshot: any concurrent put/evict either is visible to the load or is applied after the swap,
   * never lost.
   *
   * <p>A loader returning {@code null} is treated as an empty snapshot. If the loader throws, the
   * exception propagates, the existing denylist is left untouched, and readiness is not changed.
   *
   * @param snapshotLoader supplies the new full denylist contents (userId → non-ACTIVE status),
   *     invoked under the registry lock; a {@code null} result is treated as empty
   */
  void replaceAll(Supplier<Map<Long, AccountStatus>> snapshotLoader);
}
