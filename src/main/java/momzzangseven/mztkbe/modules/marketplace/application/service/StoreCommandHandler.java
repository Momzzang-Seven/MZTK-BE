package momzzangseven.mztkbe.modules.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.UpsertStoreCommandHandler;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveStorePort;
import momzzangseven.mztkbe.modules.marketplace.domain.event.TrainerStoreUpsertedEvent;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service (CommandHandler) for creating or updating a trainer store.
 *
 * <p>Single Responsibility: Upsert trainer store with business validation.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StoreCommandHandler implements UpsertStoreCommandHandler {

  private final SaveStorePort saveStorePort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public UpsertStoreResult execute(UpsertStoreCommand command) {
    log.debug("Upserting store for trainerId={}", command.trainerId());

    // Step 1: Create domain instance (TrainerStore.create handles all business validation)
    TrainerStore store =
        TrainerStore.create(
            command.trainerId(),
            command.storeName(),
            command.address(),
            command.detailAddress(),
            command.latitude(),
            command.longitude(),
            command.phoneNumber(),
            command.homepageUrl(),
            command.instagramUrl(),
            command.xUrl());

    // Step 2: Save (Native Upsert completely handles race conditions on DB side)
    TrainerStore savedStore = saveStorePort.save(store);
    log.debug("Store upserted successfully: storeId={}", savedStore.getId());

    // Step 3: Publish Domain Event for decoupled downstream modules
    eventPublisher.publishEvent(
        TrainerStoreUpsertedEvent.of(savedStore.getId(), savedStore.getTrainerId()));

    return UpsertStoreResult.from(savedStore);
  }
}
