package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidDurationException;
import momzzangseven.mztkbe.global.error.marketplace.SlotTimeConflictException;
import momzzangseven.mztkbe.global.error.marketplace.StoreNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.TrainerSuspendedException;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassTimeCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.RegisterClassCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.RegisterClassResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.RegisterClassUseCase;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.LoadTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadTrainerStorePort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.UpdateClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
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

    // Step 4: Validate slot conflicts BEFORE persisting (use placeholder classId=1L for temp slots)
    validateNoConflictsEarly(command.classTimes(), command.durationMinutes());

    // Step 5: Create domain object and persist
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

    // Step 6: Persist slots (conflict already validated)
    List<ClassSlot> slotsToSave = buildSlots(classId, command.classTimes());
    saveClassSlotPort.saveAll(slotsToSave);

    // Step 7: Save tags via tag module (upsert global tags + class_tags join table)
    List<String> tags = command.tags() != null ? command.tags() : List.of();
    manageClassTagPort.linkTagsToClass(classId, tags);

    // Step 8: Bind images
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

  /**
   * Validates time-slot conflicts using a temporary placeholder classId before any DB write.
   *
   * <p>Because {@link ClassSlot#create} requires a positive classId, a sentinel value of {@code 1L}
   * is used for the pre-save validation pass. The actual classId is assigned after the class row is
   * persisted in the subsequent step.
   */
  private void validateNoConflictsEarly(List<ClassTimeCommand> classTimes, int durationMinutes) {
    // Use sentinel classId=1L (valid positive value) solely for conflict detection
    List<ClassSlot> tempSlots =
        classTimes.stream()
            .map(ct -> ClassSlot.create(1L, ct.daysOfWeek(), ct.startTime(), ct.capacity()))
            .toList();
    for (int i = 0; i < tempSlots.size(); i++) {
      for (int j = i + 1; j < tempSlots.size(); j++) {
        if (tempSlots.get(i).conflictsWith(tempSlots.get(j), durationMinutes)) {
          throw new SlotTimeConflictException(
              "Slot " + (i + 1) + " conflicts with slot " + (j + 1));
        }
      }
    }
  }
}
