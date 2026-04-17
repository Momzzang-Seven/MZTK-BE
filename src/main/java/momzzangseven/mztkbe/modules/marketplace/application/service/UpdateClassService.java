package momzzangseven.mztkbe.modules.marketplace.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.CapacityShorterThanReservationsException;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidDurationException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.SlotHasActiveReservationException;
import momzzangseven.mztkbe.global.error.marketplace.SlotTimeConflictException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassTimeCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpdateClassCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpdateClassResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.UpdateClassUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadSlotReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.UpdateClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for updating an existing marketplace class.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Validate command and load class
 *   <li>Verify trainer ownership
 *   <li>Apply domain update (title, price, etc.)
 *   <li>Synchronise slots: add new / modify existing / soft-delete removed
 *       <ul>
 *         <li>Soft-delete blocked if slot has active reservations
 *         <li>Capacity reduction blocked if new capacity &lt; active reservation count
 *       </ul>
 *   <li>Update tags and images
 *   <li>Save the mutated class (optimistic lock)
 * </ol>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UpdateClassService implements UpdateClassUseCase {

  private final LoadClassPort loadClassPort;
  private final SaveClassPort saveClassPort;
  private final LoadClassSlotPort loadClassSlotPort;
  private final SaveClassSlotPort saveClassSlotPort;
  private final UpdateClassImagesPort updateClassImagesPort;
  private final ManageClassTagPort manageClassTagPort;
  private final LoadSlotReservationPort loadSlotReservationPort;

  @Override
  public UpdateClassResult execute(UpdateClassCommand command) {
    log.debug("Updating class: classId={}, trainerId={}", command.classId(), command.trainerId());
    command.validate();

    // Step 1: Load and verify ownership
    MarketplaceClass existingClass =
        loadClassPort
            .findById(command.classId())
            .orElseThrow(() -> new ClassNotFoundException(command.classId()));

    if (!existingClass.isOwnedBy(command.trainerId())) {
      throw new MarketplaceUnauthorizedAccessException(command.classId(), command.trainerId());
    }

    // Step 2: Validate uniform duration
    if (command.durationMinutes() <= 0) {
      throw new MarketplaceInvalidDurationException();
    }

    // Step 3: Apply domain update
    MarketplaceClass updatedClass =
        existingClass.update(
            command.title(),
            command.category(),
            command.description(),
            command.priceAmount(),
            command.durationMinutes(),
            command.tags(),
            command.features(),
            command.personalItems());

    // Step 4: Synchronise slots (비관락 내부에서 실행)
    synchroniseSlots(command.classId(), command.classTimes(), command.durationMinutes());

    // Step 5: Update tags
    List<String> tags = command.tags() != null ? command.tags() : List.of();
    manageClassTagPort.updateTags(command.classId(), tags);

    // Step 6: Update images
    List<Long> imageIds = command.imageIds() != null ? command.imageIds() : List.of();
    if (!imageIds.isEmpty()) {
      updateClassImagesPort.updateImages(command.trainerId(), command.classId(), imageIds);
    }

    // Step 7: Save class (낙관락: OptimisticLockException 발생 시 호출자가 재시도)
    saveClassPort.save(updatedClass);

    log.debug("Class updated successfully: classId={}", command.classId());
    return UpdateClassResult.of(command.classId());
  }

  // ============================================
  // Private helpers
  // ============================================

  /**
   * Synchronises the incoming classTimes with the existing persisted slots.
   *
   * <ul>
   *   <li>timeId present → update existing slot (capacity reduction blocked if below active reservations)
   *   <li>timeId absent → create new slot
   *   <li>existing slot not in incoming list → soft-delete (blocked if has active reservations)
   * </ul>
   */
  private void synchroniseSlots(
      Long classId, List<ClassTimeCommand> incomingTimes, int durationMinutes) {

    // 비관락(SELECT ... FOR UPDATE): 슬롯 capacity 변경 구간에서 예약 스레드가 구 capacity를 읽지 못하게 차단
    List<ClassSlot> existingSlots = loadClassSlotPort.findByClassIdWithLock(classId);
    Map<Long, ClassSlot> existingById =
        existingSlots.stream()
            .filter(s -> s.getId() != null)
            .collect(Collectors.toMap(ClassSlot::getId, s -> s));

    Set<Long> incomingTimeIds =
        incomingTimes.stream()
            .filter(ct -> ct.timeId() != null)
            .map(ClassTimeCommand::timeId)
            .collect(Collectors.toSet());

    List<ClassSlot> slotsToSave = new ArrayList<>();

    for (ClassTimeCommand ct : incomingTimes) {
      if (ct.timeId() != null && existingById.containsKey(ct.timeId())) {
        // Update existing slot — check capacity constraint against active reservations
        ClassSlot existing = existingById.get(ct.timeId());
        if (existing.getId() != null && ct.capacity() < existing.getCapacity()) {
          int activeReservations = loadSlotReservationPort.countActiveReservations(existing.getId());
          if (ct.capacity() < activeReservations) {
            throw new CapacityShorterThanReservationsException(activeReservations, ct.capacity());
          }
        }
        ClassSlot updated = existing.update(ct.daysOfWeek(), ct.startTime(), ct.capacity());
        slotsToSave.add(updated);
      } else {
        // Create new slot
        slotsToSave.add(ClassSlot.create(classId, ct.daysOfWeek(), ct.startTime(), ct.capacity()));
      }
    }

    // Conflict validation runs only over the ACTIVE result set (new + updated slots).
    // Soft-delete candidates are intentionally excluded: inactive slots cannot be booked,
    // so they do not participate in schedule conflicts.
    // Soft-delete targets are appended AFTER this check to keep them out of the validation scope.
    validateNoConflicts(slotsToSave, durationMinutes);

    // Handle missing slots (deletion candidates) — check for active reservations before soft-delete
    for (ClassSlot existing : existingSlots) {
      if (existing.getId() != null
          && !incomingTimeIds.contains(existing.getId())
          && existing.isActive()) {
        if (loadSlotReservationPort.hasActiveReservation(existing.getId())) {
          throw new SlotHasActiveReservationException(existing.getId());
        }
        slotsToSave.add(existing.softDelete());
      }
    }

    saveClassSlotPort.saveAll(slotsToSave);
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
