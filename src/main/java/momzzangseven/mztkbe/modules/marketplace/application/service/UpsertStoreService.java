package momzzangseven.mztkbe.modules.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.UpsertStoreUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadStorePort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveStorePort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for creating or updating a trainer store.
 *
 * <p>Handles the upsert (create-or-update) flow at the application layer:
 *
 * <ol>
 *   <li>Validate command
 *   <li>Create domain model with business validation
 *   <li>Check if a store already exists for this trainer
 *   <li>If exists: update via domain model's toBuilder() (immutable pattern), then JPA save
 *   <li>If not: save as new entity via JPA save
 * </ol>
 *
 * <p>The create-or-update branching logic lives HERE (application layer), not in the persistence
 * adapter. The adapter is a thin JPA pass-through following the project convention.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UpsertStoreService implements UpsertStoreUseCase {

  private final LoadStorePort loadStorePort;
  private final SaveStorePort saveStorePort;

  @Override
  public UpsertStoreResult execute(UpsertStoreCommand command) {
    log.debug("Upserting store for trainerId={}", command.trainerId());

    // Step 1: Validate command (project convention — all services call command.validate())
    command.validate();

    // Step 2: Create domain instance (TrainerStore.create handles all business invariants)
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
            command.xProfileUrl());

    // Step 3: Check if store already exists (upsert branching in application layer)
    TrainerStore savedStore =
        loadStorePort
            .findByTrainerId(command.trainerId())
            .map(
                existing -> {
                  // Update: carry over the existing ID so JPA performs UPDATE (not INSERT)
                  TrainerStore updated = store.toBuilder().id(existing.getId()).build();
                  log.debug("Existing store found (id={}), updating...", existing.getId());
                  return saveStorePort.save(updated);
                })
            .orElseGet(
                () -> {
                  log.debug("No existing store, creating new...");
                  return saveStorePort.save(store);
                });

    log.debug("Store upserted successfully: storeId={}", savedStore.getId());

    return UpsertStoreResult.from(savedStore);
  }
}
