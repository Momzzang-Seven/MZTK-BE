package momzzangseven.mztkbe.modules.marketplace.domain.vo;

/**
 * Domain event emitted when a trainer is banned due to accumulating 3 or more strikes.
 *
 * <p>This plain record carries no framework dependencies and is published via Spring's
 * ApplicationEventPublisher from within the sanction module's service. The classes module listens
 * to this event to deactivate all of the trainer's active listings.
 *
 * @param trainerId the ID of the trainer who has been banned
 */
public record TrainerBannedEvent(Long trainerId) {}
