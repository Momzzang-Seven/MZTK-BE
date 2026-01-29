package momzzangseven.mztkbe.modules.user.domain.event;

import java.util.List;

/**
 * Domain event published when users are hard-deleted
 *
 * <p>Other modules can listen to this event to perform cascade deletion of their data associated
 * with these users.
 *
 * @param userIds IDs of users that have been hard-deleted
 */
public record UsersHardDeletedEvent(List<Long> userIds) {}
