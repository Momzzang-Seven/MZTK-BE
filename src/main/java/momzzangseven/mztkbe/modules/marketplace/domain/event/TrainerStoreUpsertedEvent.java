package momzzangseven.mztkbe.modules.marketplace.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a trainer store is successfully upserted.
 *
 * @param eventId unique event identifier for idempotency
 * @param occurredAt timestamp when the event occurred
 * @param storeId The ID of the upserted store
 * @param trainerId The ID of the trainer
 */
public record TrainerStoreUpsertedEvent(
    UUID eventId, Instant occurredAt, Long storeId, Long trainerId) {

  /**
   * Factory method that auto-generates eventId and occurredAt.
   *
   * @param storeId the upserted store ID
   * @param trainerId the trainer's user ID
   * @return a new event instance with generated metadata
   */
  public static TrainerStoreUpsertedEvent of(Long storeId, Long trainerId) {
    return new TrainerStoreUpsertedEvent(UUID.randomUUID(), Instant.now(), storeId, trainerId);
  }
}
