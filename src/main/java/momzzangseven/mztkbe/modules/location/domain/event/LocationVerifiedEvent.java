package momzzangseven.mztkbe.modules.location.domain.event;

import java.time.LocalDateTime;

/**
 * Domain event published when a location verification succeeds.
 *
 * <p>Carries only the producer's own facts (user, location, and the verification moment). It
 * intentionally does not reference any {@code level}-module concept (e.g. {@code XpType} or
 * idempotency keys) so that the location module stays decoupled from XP rewards.
 *
 * <p>{@code verifiedAt} is the verification moment already resolved to the application time zone,
 * so downstream consumers can derive the daily-cap earned-date and idempotency key from the action
 * time rather than from their own execution time.
 *
 * @param userId the verifying user's id
 * @param locationId the verified location's id
 * @param verifiedAt the verification moment, in the application time zone
 */
public record LocationVerifiedEvent(Long userId, Long locationId, LocalDateTime verifiedAt) {}
