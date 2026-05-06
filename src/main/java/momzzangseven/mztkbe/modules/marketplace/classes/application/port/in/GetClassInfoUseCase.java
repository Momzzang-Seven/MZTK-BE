package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;

/**
 * Input port that exposes class aggregate lookup to other modules.
 *
 * <p>Cross-module callers (e.g., the {@code reservation} module) must use this interface instead of
 * directly referencing the output port {@code LoadClassPort}. This keeps the dependency direction
 * correct: only {@code application/port/in/} is the public API surface of a module.
 */
public interface GetClassInfoUseCase {

  /**
   * Find a class by its ID.
   *
   * @param classId class ID
   * @return Optional containing the class aggregate if found
   */
  Optional<MarketplaceClass> findById(Long classId);

  /**
   * Find the class that owns the given slot, without acquiring a lock.
   *
   * <p>Intended for read-only cross-module enrichment (e.g., the {@code reservation} module
   * populating classId from a slotId). Uses a plain {@code SELECT} — do not use when you need
   * concurrent-write protection; use {@link
   * momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassSlotInfoUseCase#findByIdWithLock}
   * for that.
   *
   * @param slotId slot ID
   * @return Optional containing the class aggregate if the slot and its class are found
   */
  Optional<MarketplaceClass> findBySlotId(Long slotId);
}
