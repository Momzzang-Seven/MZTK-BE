package momzzangseven.mztkbe.modules.admin.application.port.in;

/**
 * Input port for checking whether an admin account exists and is active for a given user. Used by
 * cross-cutting concerns (e.g. security filters) that need to verify admin account status without
 * importing admin internals.
 */
public interface CheckAdminAccountStatusUseCase {

  /** Returns {@code true} if an active (non-deleted) admin account exists for the given user. */
  boolean isActiveAdmin(Long userId);
}
