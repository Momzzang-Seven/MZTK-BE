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
}
