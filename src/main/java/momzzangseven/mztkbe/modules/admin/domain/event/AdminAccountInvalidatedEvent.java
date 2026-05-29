package momzzangseven.mztkbe.modules.admin.domain.event;

/**
 * Published after an {@link momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount} write
 * (save / delete) so that the in-process admin-active cache can drop its stale entry for {@code
 * userId}.
 *
 * <p>Consumed via {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 *
 * @param userId user whose admin-active flag is no longer valid
 */
public record AdminAccountInvalidatedEvent(Long userId) {}
