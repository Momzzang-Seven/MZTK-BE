package momzzangseven.mztkbe.modules.marketplace.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.domain.model.ClassSlot;

/**
 * Output port for persisting class time-slots.
 *
 * <p>Supports bulk save and hard delete (for slots that have never been reserved) as well as
 * soft-delete via the domain model (slot.active = false).
 */
public interface SaveClassSlotPort {

  /**
   * Save (insert or update) a list of class slots.
   *
   * @param slots list of domain models to persist
   * @return saved domain models with generated IDs
   */
  List<ClassSlot> saveAll(List<ClassSlot> slots);

  /**
   * Hard-delete a class slot by ID. Only called when the slot has never had a reservation.
   *
   * @param slotId slot ID to delete
   */
  void deleteById(Long slotId);
}
