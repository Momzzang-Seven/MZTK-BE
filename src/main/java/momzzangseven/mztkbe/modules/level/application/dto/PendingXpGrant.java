package momzzangseven.mztkbe.modules.level.application.dto;

/**
 * Application-layer view of a queued XP-grant awaiting reconciliation.
 *
 * @param id outbox row id
 * @param command the original grant command to replay (idempotent)
 * @param attemptCount how many times processing has already failed
 */
public record PendingXpGrant(Long id, GrantXpCommand command, int attemptCount) {}
