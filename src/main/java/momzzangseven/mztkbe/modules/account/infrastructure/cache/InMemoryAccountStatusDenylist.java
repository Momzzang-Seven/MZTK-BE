package momzzangseven.mztkbe.modules.account.infrastructure.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 *   <li>{@link #replaceAll} builds a brand-new map and publishes it via a single atomic reference
 *       swap (reconcile), so readers never observe a half-built map.
 * </ul>
 *
 * <p>Per design §5 ("Reconcile 정합성 & race 자가치유"), a residual race remains between reading the
 * reconcile snapshot and the swap: incremental events landing in that window can be clobbered by
 * the swap. This is accepted — mutations are low-frequency, the next event/reconcile self-heals,
 * and the worst-case exposure is bounded by the access-token TTL.
 */
@Component
public class InMemoryAccountStatusDenylist
    implements LoadAccountStatusRegistryPort, UpdateAccountStatusRegistryPort {

  private volatile ConcurrentHashMap<Long, AccountStatus> map = new ConcurrentHashMap<>();
  private volatile boolean ready = false;

  @Override
  public AccountStatus statusOf(Long userId) {
    // Read the volatile reference once into a local so a concurrent replaceAll swap
    // cannot be observed mid-read.
    ConcurrentHashMap<Long, AccountStatus> current = this.map;
    return current.getOrDefault(userId, AccountStatus.ACTIVE);
  }

  @Override
  public void put(Long userId, AccountStatus status) {
    this.map.put(userId, status);
  }

  @Override
  public void evict(Long userId) {
    this.map.remove(userId);
  }

  @Override
  public void replaceAll(Map<Long, AccountStatus> snapshot) {
    // Build a brand-new map and publish it with a single atomic reference swap;
    // never putAll/removeIf on the live map (would expose a half-built state to readers).
    this.map = snapshot == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(snapshot);
    // Mark ready only after the map reference is swapped. An empty snapshot is still a valid
    // ready state ("loaded from DB at least once"), so readiness is independent of map size.
    this.ready = true;
  }

  @Override
  public boolean isReady() {
    return ready;
  }
}
