package momzzangseven.mztkbe.modules.account.domain.event;

/**
 * Domain event published when a user account is soft-deleted (withdrawn).
 *
 * <p>Other modules can listen to this event to perform cascade action of their data associated with
 * this user.
 *
 * @param userId ID of user that has been soft-deleted
 */
public record UserSoftDeletedEvent(Long userId) {}
