package momzzangseven.mztkbe.modules.account.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/**
 * Input port for checking the status of a user account. Used by cross-cutting concerns (e.g.
 * security filters, token reissue) that need to know whether an account is active or deleted
 * without importing account internals.
 *
 * <p>Implementations are expected to be cached so that repeated calls for the same {@code userId}
 * within a short window don't hit the DB.
 */
public interface CheckAccountStatusUseCase {

  /** Returns {@code true} if the account associated with the given user is ACTIVE. */
  boolean isActive(Long userId);

  /** Returns {@code true} if the account associated with the given user is DELETED (withdrawn). */
  boolean isDeleted(Long userId);

  /** Returns {@code true} if the account associated with the given user is BLOCKED. */
  boolean isBlocked(Long userId);

  /**
   * Returns the raw {@link AccountStatus} for callers that need to branch on the specific status
   * rather than three boolean checks. Empty when no account exists for {@code userId}.
   */
  Optional<AccountStatus> findStatus(Long userId);
}
