package momzzangseven.mztkbe.modules.account.domain.event;

/**
 * Published after a {@link momzzangseven.mztkbe.modules.account.domain.model.UserAccount} write
 * (save / delete) so that the in-process status cache can drop its stale entry for {@code userId}.
 *
 * <p>Consumed via {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — the cache is only
 * invalidated after the surrounding transaction commits, so a rolled-back write leaves the cache
 * untouched.
 *
 * @param userId user whose account status snapshot is no longer valid
 */
public record UserAccountInvalidatedEvent(Long userId) {}
