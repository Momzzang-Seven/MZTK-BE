package momzzangseven.mztkbe.modules.account.application.port.in;

/**
 * Input port for checking the status of a user account. Used by cross-cutting concerns (e.g.
 * security filters) that need to know whether an account is active or deleted without importing
 * account internals.
 */
public interface CheckAccountStatusUseCase {

  /** Returns {@code true} if the account associated with the given user is ACTIVE. */
  boolean isActive(Long userId);

  /** Returns {@code true} if the account associated with the given user is DELETED (withdrawn). */
  boolean isDeleted(Long userId);

  /** Returns {@code true} if the account associated with the given user is BLOCKED. */
  boolean isBlocked(Long userId);
}
