package momzzangseven.mztkbe.modules.user.domain.event;

/**
 * Domain event published when users are soft-deleted
 *
 * <p>Other modules can listen to this event to perform cascade action of their data associated with
 * these users.
 *
 * @param userId ID of user that have been soft-deleted
 */
public record UserSoftDeletedEvent(Long userId) {}
