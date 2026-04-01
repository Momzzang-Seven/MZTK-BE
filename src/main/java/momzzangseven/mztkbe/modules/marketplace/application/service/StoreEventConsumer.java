package momzzangseven.mztkbe.modules.marketplace.application.service;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.domain.event.TrainerStoreUpsertedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Consumes domain events related to trainer stores.
 *
 * <p>Uses {@code AFTER_COMMIT} to ensure the primary transaction has succeeded before executing
 * side effects. This consumer provides a decoupling point for downstream modules (e.g., search
 * index update, push notification) that need to react to store changes.
 *
 * <p><b>Outbox pattern migration:</b> When implementing the Outbox pattern, change {@code phase} to
 * {@code BEFORE_COMMIT} so that outbox record persistence is atomic with the main transaction.
 */
@Slf4j
@Component
public class StoreEventConsumer {

  /**
   * Handle TrainerStoreUpsertedEvent after the transaction has successfully committed.
   *
   * <p>Currently logs the event for observability. Replace with actual side-effect logic (outbox
   * persistence, secondary DB update, push notification) when requirements emerge.
   *
   * @param event the store upserted event
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleTrainerStoreUpsertedEvent(TrainerStoreUpsertedEvent event) {
    log.debug(
        "TrainerStoreUpsertedEvent received. eventId={}, storeId={}, trainerId={}",
        event.eventId(),
        event.storeId(),
        event.trainerId());

    // TODO: Implement outbox pattern persistence, secondary DB update, or push notifications here.
  }
}
