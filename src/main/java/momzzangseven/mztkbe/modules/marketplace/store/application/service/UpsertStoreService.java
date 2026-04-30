package momzzangseven.mztkbe.modules.marketplace.store.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.store.application.dto.UpsertStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.store.application.dto.UpsertStoreResult;
import momzzangseven.mztkbe.modules.marketplace.store.application.port.in.UpsertStoreUseCase;
import momzzangseven.mztkbe.modules.marketplace.store.application.port.out.LoadStorePort;
import momzzangseven.mztkbe.modules.marketplace.store.application.port.out.SaveStorePort;
import momzzangseven.mztkbe.modules.marketplace.store.domain.model.TrainerStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for creating or updating a trainer store.
 *
 * <p>Handles the upsert (create-or-update) flow at the application layer:
 *
 * <ol>
 *   <li>Validate command
 *   <li>Check if a store already exists for this trainer
 *   <li>If exists: delegate to domain model's {@code update()} method (validation enforced)
 *   <li>If not: create via domain model's {@code create()} factory method (validation enforced)
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

    // Step 2: Check if store already exists (upsert branching in application layer)
    TrainerStore savedStore =
        loadStorePort
            .findByTrainerId(command.trainerId())
            .map(
                existing -> {
                  // Update: domain's update() validates all fields and preserves identity
                  TrainerStore updated =
                      existing.update(
                          command.storeName(),
                          command.address(),
                          command.detailAddress(),
                          command.latitude(),
                          command.longitude(),
                          command.phoneNumber(),
                          command.homepageUrl(),
                          command.instagramUrl(),
                          command.xProfileUrl());
                  log.debug("Existing store found (id={}), updating...", existing.getId());
                  return saveStorePort.save(updated);
                })
            .orElseGet(
                () -> {
                  // Create: factory method validates all fields
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
                  log.debug("No existing store, creating new...");
                  return saveStorePort.save(store);
                });

    log.debug("Store upserted successfully: storeId={}", savedStore.getId());

    return UpsertStoreResult.from(savedStore);
  }
}
