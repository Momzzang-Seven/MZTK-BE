package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.CapacityShorterThanReservationsException;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidSlotException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.SlotHasActiveReservationException;
import momzzangseven.mztkbe.global.error.marketplace.SlotTimeConflictException;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassTimeCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.UpdateClassCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.UpdateClassResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.UpdateClassUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadSlotReservationPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.UpdateClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for updating an existing marketplace class.
 *
 * <h2>Flow</h2>
 *
 * <ol>
 *   <li>Validate command fields
 *   <li>Load class and verify ownership
 *   <li>Lock slots pessimistically to prevent concurrent reservation races
 *   <li>Synchronise slots — add new, update existing, soft/hard-delete removed
 *   <li>Validate no time conflicts among the final active slot set
 *   <li>Update class metadata via the domain model
 *   <li>Persist in order: slots → tags → images → class
 * </ol>
 *
 * <h2>Slot synchronisation rules</h2>
 *
 * <ul>
 *   <li>{@code timeId != null} — update the matching existing slot. Capacity must not drop below
 *       the current active-reservation count.
 *   <li>{@code timeId == null} — treat as a brand-new slot.
 *   <li>Slot present in DB but absent from the request — remove it. If any <em>active</em>
 *       reservations exist the removal is forbidden ({@link SlotHasActiveReservationException}). If
 *       historical reservations exist but none are active, the slot is soft-deleted ({@code
 *       active=false}). If no reservations exist at all, the slot is hard-deleted.
 * </ul>
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
  private final LoadSlotReservationPort loadSlotReservationPort;
  private final ManageClassTagPort manageClassTagPort;
  private final UpdateClassImagesPort updateClassImagesPort;

  @Override
  public UpdateClassResult execute(UpdateClassCommand command) {
    log.debug("Updating class: classId={}, trainerId={}", command.classId(), command.trainerId());
    command.validate();

    // Step 1: Load class + ownership check
    MarketplaceClass marketplaceClass =
        loadClassPort
            .findById(command.classId())
            .orElseThrow(() -> new ClassNotFoundException(command.classId()));

    if (!marketplaceClass.isOwnedBy(command.trainerId())) {
      throw new MarketplaceUnauthorizedAccessException(command.classId(), command.trainerId());
    }

    // Step 2: Load current active slots under pessimistic write lock
    List<ClassSlot> existingSlots = loadClassSlotPort.findByClassIdWithLock(command.classId());
    Map<Long, ClassSlot> existingActiveById = buildActiveSlotMap(existingSlots);

    // Step 3: Synchronise slots
    List<ClassSlot> slotsToSave = syncSlots(command, existingActiveById);

    // Step 4: Validate no conflicts among final slots.
    // slotsToSave contains ALL final active slots (new + updated + retained existing)
    // because syncSlots() iterates over every entry in existingActiveById and includes
    // retained slots. No separate comparison against the DB state is needed.
    validateNoConflicts(slotsToSave, command.durationMinutes());

    // Step 5: Persist slots
    if (!slotsToSave.isEmpty()) {
      saveClassSlotPort.saveAll(slotsToSave);
    }

    // Step 6: Update domain object
    MarketplaceClass updated =
        marketplaceClass.update(
            command.title(),
            command.category(),
            command.description(),
            command.priceAmount(),
            command.durationMinutes(),
            command.tags(),
            command.features(),
            command.personalItems());

    // Step 7: Update tags
    List<String> tags = command.tags() != null ? command.tags() : List.of();
    manageClassTagPort.updateTags(command.classId(), tags);

    // Step 8: Update images
    // Per UpdateClassImagesPort contract: "empty list removes all images".
    // We must call updateImages() even when imageIds is empty so that an explicit
    // empty payload removes all previously bound images.
    // Only skip when imageIds is null (field was absent from the request).
    if (command.imageIds() != null) {
      updateClassImagesPort.updateImages(
          command.trainerId(), command.classId(), command.imageIds());
    }

    // Step 9: Save updated class metadata
    MarketplaceClass saved = saveClassPort.save(updated);

    log.debug("Class updated successfully: classId={}", saved.getId());
    return UpdateClassResult.of(saved.getId());
  }

  // ============================================
  // Private helpers
  // ============================================

  /**
   * Builds a map from slot ID to {@link ClassSlot} for all currently <em>active</em> slots.
   *
   * @param slots all slots belonging to the class (active and inactive)
   * @return map keyed by slot ID
   */
  private Map<Long, ClassSlot> buildActiveSlotMap(List<ClassSlot> slots) {
    Map<Long, ClassSlot> map = new HashMap<>();
    for (ClassSlot slot : slots) {
      if (slot.isActive() && slot.getId() != null) {
        map.put(slot.getId(), slot);
      }
    }
    return map;
  }

  /**
   * Synchronises the requested slot list against the persisted slots and returns the new slot list
   * to save.
   *
   * <p>Slots removed from the request are soft- or hard-deleted depending on reservation history.
   * Active reservations block all removal.
   *
   * @param command the update command
   * @param existingActiveById currently active slots indexed by ID
   * @return list of {@link ClassSlot} domain models ready to be persisted (new + updated +
   *     soft-deleted)
   */
  private List<ClassSlot> syncSlots(
      UpdateClassCommand command, Map<Long, ClassSlot> existingActiveById) {

    List<ClassSlot> result = new ArrayList<>();
    Set<Long> processedIds = new HashSet<>();
    Long classId = command.classId();

    // Batch fetch active reservations for existing slots
    List<Long> existingSlotIds = new ArrayList<>(existingActiveById.keySet());
    Map<Long, Integer> activeReservationCounts =
        existingSlotIds.isEmpty()
            ? new HashMap<>()
            : loadSlotReservationPort.countActiveReservationsIn(existingSlotIds);

    for (ClassTimeCommand ct : command.classTimes()) {
      if (ct.timeId() != null) {
        // ── Update existing slot ──────────────────────────────────────────────
        ClassSlot existing = existingActiveById.get(ct.timeId());
        if (existing == null) {
          throw new MarketplaceInvalidSlotException(
              "Slot not found or not active: timeId=" + ct.timeId());
        }

        // Capacity reduction guard: must not go below active reservation count
        int activeReservations = activeReservationCounts.getOrDefault(ct.timeId(), 0);
        if (ct.capacity() < activeReservations) {
          throw new CapacityShorterThanReservationsException(activeReservations, ct.capacity());
        }

        result.add(existing.update(ct.daysOfWeek(), ct.startTime(), ct.capacity()));
        processedIds.add(ct.timeId());
      } else {
        // ── Brand-new slot ────────────────────────────────────────────────────
        result.add(ClassSlot.create(classId, ct.daysOfWeek(), ct.startTime(), ct.capacity()));
      }
    }

    // ── Remove slots absent from the request ─────────────────────────────────
    for (Map.Entry<Long, ClassSlot> entry : existingActiveById.entrySet()) {
      Long slotId = entry.getKey();
      ClassSlot slot = entry.getValue();

      if (!processedIds.contains(slotId)) {
        int activeReservations = activeReservationCounts.getOrDefault(slotId, 0);
        if (activeReservations > 0) {
          // Active reservations exist — cannot remove
          throw new SlotHasActiveReservationException(slotId);
        }

        boolean hasHistory = loadSlotReservationPort.hasAnyReservationHistory(slotId);
        if (hasHistory) {
          // Historical reservations exist — soft delete to preserve audit trail
          result.add(slot.softDelete());
        } else {
          // No reservation history at all — hard delete
          saveClassSlotPort.deleteById(slotId);
        }
      }
    }

    return result;
  }

  /**
   * Validates that no two active (non-soft-deleted) slots in {@code slots} overlap in time.
   *
   * <p>Only slots that are still active (i.e. not produced by {@link ClassSlot#softDelete()}) are
   * included in the conflict check.
   *
   * @param slots final slot list to persist
   * @param durationMinutes shared session duration for all slots
   * @throws SlotTimeConflictException if any two active slots have overlapping intervals
   */
  private void validateNoConflicts(List<ClassSlot> slots, int durationMinutes) {
    List<ClassSlot> activeSlots = slots.stream().filter(ClassSlot::isActive).toList();
    for (int i = 0; i < activeSlots.size(); i++) {
      for (int j = i + 1; j < activeSlots.size(); j++) {
        if (activeSlots.get(i).conflictsWith(activeSlots.get(j), durationMinutes)) {
          throw new SlotTimeConflictException(
              "Slot " + (i + 1) + " conflicts with slot " + (j + 1));
        }
      }
    }
  }
}
