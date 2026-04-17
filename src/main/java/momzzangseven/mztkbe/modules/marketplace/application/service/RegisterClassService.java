package momzzangseven.mztkbe.modules.marketplace.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidDurationException;
import momzzangseven.mztkbe.global.error.marketplace.SlotTimeConflictException;
import momzzangseven.mztkbe.global.error.marketplace.StoreNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.TrainerSuspendedException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassTimeCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RegisterClassCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RegisterClassResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.RegisterClassUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadTrainerStorePort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.UpdateClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for registering a new marketplace class.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Validate command
 *   <li>Assert trainer has a registered store
 *   <li>Assert trainer is not suspended
 *   <li>Create domain objects and persist (class + slots)
 *   <li>Bind images via the image module
 * </ol>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RegisterClassService implements RegisterClassUseCase {

  private final LoadTrainerStorePort loadTrainerStorePort;
  private final LoadTrainerSanctionPort loadTrainerSanctionPort;
  private final SaveClassPort saveClassPort;
  private final SaveClassSlotPort saveClassSlotPort;
  private final UpdateClassImagesPort updateClassImagesPort;
  private final ManageClassTagPort manageClassTagPort;

  @Override
  public RegisterClassResult execute(RegisterClassCommand command) {
    log.debug("Registering class for trainerId={}", command.trainerId());
    command.validate();

    // Step 1: Verify store exists
    loadTrainerStorePort
        .findByTrainerId(command.trainerId())
        .orElseThrow(() -> new StoreNotFoundException(command.trainerId()));

    // Step 2: Verify trainer is not suspended
    if (loadTrainerSanctionPort.hasActiveSanction(command.trainerId())) {
      throw new TrainerSuspendedException(command.trainerId());
    }

    // Step 3: Validate positive duration (defensive guard; domain and Bean Validation also check)
    validatePositiveDuration(command.durationMinutes());

    // Step 4: Create domain object and persist
    MarketplaceClass marketplaceClass =
        MarketplaceClass.create(
            command.trainerId(),
            command.title(),
            command.category(),
            command.description(),
            command.priceAmount(),
            command.durationMinutes(),
            command.tags(),
            command.features(),
            command.personalItems());

    MarketplaceClass savedClass = saveClassPort.save(marketplaceClass);
    Long classId = savedClass.getId();

    // Step 5: Persist slots
    List<ClassSlot> slotsToSave = buildSlots(classId, command.classTimes());
    validateNoConflicts(slotsToSave, command.durationMinutes());
    saveClassSlotPort.saveAll(slotsToSave);

    // Step 6: Save tags via tag module (upsert global tags + class_tags join table)
    List<String> tags = command.tags() != null ? command.tags() : List.of();
    manageClassTagPort.linkTagsToClass(classId, tags);

    // Step 7: Bind images
    List<Long> imageIds = command.imageIds() != null ? command.imageIds() : List.of();
    if (!imageIds.isEmpty()) {
      updateClassImagesPort.updateImages(command.trainerId(), classId, imageIds);
    }

    log.debug("Class registered successfully: classId={}", classId);
    return RegisterClassResult.of(classId);
  }

  // ============================================
  // Private helpers
  // ============================================

  private List<ClassSlot> buildSlots(Long classId, List<ClassTimeCommand> classTimes) {
    return classTimes.stream()
        .map(ct -> ClassSlot.create(classId, ct.daysOfWeek(), ct.startTime(), ct.capacity()))
        .toList();
  }

  /**
   * Defensive guard ensuring durationMinutes is positive.
   *
   * <p>Bean Validation ({@code @Min(1)}) and the domain model ({@code validateDuration}) both
   * enforce this constraint. This guard prevents a future schema change (e.g., per-slot duration)
   * from silently bypassing the class-level duration check.
   */
  private void validatePositiveDuration(int durationMinutes) {
    if (durationMinutes <= 0) {
      throw new MarketplaceInvalidDurationException(
          "Duration must be positive: " + durationMinutes);
    }
  }

  private void validateNoConflicts(List<ClassSlot> slots, int durationMinutes) {
    for (int i = 0; i < slots.size(); i++) {
      for (int j = i + 1; j < slots.size(); j++) {
        if (slots.get(i).conflictsWith(slots.get(j), durationMinutes)) {
          throw new SlotTimeConflictException(
              "Slot " + (i + 1) + " conflicts with slot " + (j + 1));
        }
      }
    }
  }
}
