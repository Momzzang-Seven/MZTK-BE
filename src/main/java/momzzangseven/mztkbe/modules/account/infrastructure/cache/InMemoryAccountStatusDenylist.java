package momzzangseven.mztkbe.modules.account.infrastructure.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.application.port.out.UpdateAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.stereotype.Component;

/**
 * In-memory negative cache (denylist) of non-ACTIVE account statuses backing the auth hot path.
 *
 * <p>Only non-ACTIVE users are stored, so an absent userId reads as {@link AccountStatus#ACTIVE}.
 * Reads ({@link #statusOf}) are pure in-memory lookups acquiring zero database connections.
 *
 * <p>Backed by a {@code volatile} reference to a {@link ConcurrentHashMap}:
 *
 * <ul>
 *   <li>{@link #put} / {@link #evict} mutate the current map in place (incremental event updates).
 *   <li>{@link #replaceAll} loads a fresh snapshot and publishes it via a single atomic reference
 *       swap (reconcile), so readers never observe a half-built map.
 * </ul>
 *
 * <p><strong>Write serialization.</strong> A {@link ReentrantLock} serializes {@link #replaceAll}'s
 * snapshot-load-plus-swap against the incremental {@link #put} / {@link #evict}. Because the
 * reconcile DB read runs <em>inside</em> the lock, an event cannot interleave between "snapshot
 * read" and "swap": a concurrent put/evict either is visible to the load or is re-applied after the
 * swap, so it is never clobbered by a stale snapshot. The read path ({@link #statusOf}) is
 * deliberately <strong>lock-free</strong> (a single volatile read) so the auth hot path never
 * contends on this lock.
 */
@Component
public class InMemoryAccountStatusDenylist
    implements LoadAccountStatusRegistryPort, UpdateAccountStatusRegistryPort {

  private final ReentrantLock writeLock = new ReentrantLock();
  private volatile ConcurrentHashMap<Long, AccountStatus> map = new ConcurrentHashMap<>();
  private volatile boolean ready = false;

  @Override
  public AccountStatus statusOf(Long userId) {
    // Lock-free hot path: read the volatile reference once into a local so a concurrent replaceAll
    // swap cannot be observed mid-read.
    ConcurrentHashMap<Long, AccountStatus> current = this.map;
    return current.getOrDefault(userId, AccountStatus.ACTIVE);
  }

  @Override
  public void put(Long userId, AccountStatus status) {
    // Locked (not for ConcurrentHashMap safety, but) to order this mutation against replaceAll's
    // load+swap so it is never lost in the reconcile window.
    writeLock.lock();
    try {
      this.map.put(userId, status);
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void evict(Long userId) {
    // Locked to order this mutation against replaceAll's load+swap (see put).
    writeLock.lock();
    try {
      this.map.remove(userId);
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void replaceAll(Supplier<Map<Long, AccountStatus>> snapshotLoader) {
    // Hold the lock across the snapshot load AND the swap so put/evict cannot interleave between
    // them. If the loader throws, the map/ready state is left untouched and the exception
    // propagates (the caller is fail-soft).
    writeLock.lock();
    try {
      Map<Long, AccountStatus> snapshot = snapshotLoader.get();
      // Build a brand-new map and publish it with a single atomic reference swap; never
      // putAll/removeIf on the live map (would expose a half-built state to lock-free readers).
      this.map = snapshot == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(snapshot);
      // Mark ready only after the map reference is swapped. An empty snapshot is still a valid
      // ready state ("loaded from DB at least once"), so readiness is independent of map size.
      this.ready = true;
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public boolean isReady() {
    return ready;
  }
}
