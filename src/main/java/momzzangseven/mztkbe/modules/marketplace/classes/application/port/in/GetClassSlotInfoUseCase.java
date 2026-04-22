package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;

/**
 * Input port that exposes class slot lookup (with pessimistic lock) to other modules.
 *
 * <p>Cross-module callers (e.g., the {@code reservation} module) must use this interface instead
 * of referencing the output port {@code LoadClassSlotPort} directly.
 */
public interface GetClassSlotInfoUseCase {

  /**
   * Find a single slot by its own ID under a pessimistic write lock.
   *
   * <p>Used by the reservation service to verify slot existence and ownership while preventing
   * concurrent over-commit.
   *
   * @param slotId slot ID
   * @return Optional containing the slot if found (locked)
   */
  Optional<ClassSlot> findByIdWithLock(Long slotId);
}
