package momzzangseven.mztkbe.modules.account.domain.event;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/**
 * Published from the persistence adapter ONLY when an account undergoes a real status transition
 * (e.g. ACTIVE → BLOCKED). The new {@link AccountStatus} is carried in the event so consumers can
 * update the in-memory denylist without a DB re-read.
 *
 * <p>Consumed via {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — the denylist is only
 * mutated after the surrounding transaction commits, so a rolled-back write leaves it untouched.
 *
 * @param userId user whose account status transitioned
 * @param status the new account status after the transition
 */
public record UserAccountStatusChangedEvent(Long userId, AccountStatus status) {}
