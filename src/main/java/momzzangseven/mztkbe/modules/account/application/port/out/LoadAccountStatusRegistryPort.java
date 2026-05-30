package momzzangseven.mztkbe.modules.account.application.port.out;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/**
 * Read side of the in-memory account-status denylist (negative cache).
 *
 * <p>This is the auth hot-path lookup: it must be a pure in-memory read that acquires zero database
 * connections. Only non-ACTIVE users are tracked, so an absent userId is interpreted as ACTIVE.
 */
public interface LoadAccountStatusRegistryPort {

  /**
   * Returns the user's non-ACTIVE status if present in the denylist, otherwise {@link
   * AccountStatus#ACTIVE} (absence = ACTIVE). Never returns {@code null}.
   *
   * @param userId the user identifier to look up
   * @return the tracked non-ACTIVE {@link AccountStatus}, or {@link AccountStatus#ACTIVE} if absent
   */
  AccountStatus statusOf(Long userId);

  /**
   * Returns whether the registry has been successfully loaded from the DB at least once.
   *
   * <p>This is a readiness flag, not an emptiness check: a successful load of zero non-ACTIVE users
   * is a valid ready state. It is {@code false} only before the first successful {@code replaceAll}
   * swap. The warm-up runner uses this to decide whether the denylist is safe to serve traffic
   * (fail-closed boot when it never becomes ready).
   *
   * @return {@code true} once at least one DB load has populated the registry, otherwise {@code
   *     false}
   */
  boolean isReady();
}
