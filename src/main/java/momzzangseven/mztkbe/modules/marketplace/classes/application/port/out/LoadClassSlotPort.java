package momzzangseven.mztkbe.modules.marketplace.classes.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;

/**
 * Output port for loading class time-slots from persistence.
 *
 * <p>Provides three access modes:
 *
 * <ul>
 *   <li>{@link #findByClassId} — no lock; used for conflict-checking during class registration and
 *       for display.
 *   <li>{@link #findById} — no lock; single-slot lookup used for read-only cross-module enrichment
 *       (e.g., resolving slotId → classId for reservation responses).
 *   <li>{@link #findByClassIdWithLock} — {@code SELECT ... FOR UPDATE}; used when modifying slot
 *       capacity to prevent concurrent reservation races.
 * </ul>
 */
public interface LoadClassSlotPort {

  /**
   * Find all slots (active and inactive) belonging to a class, without a lock.
   *
   * @param classId class ID
   * @return list of ClassSlot domain models
   */
  List<ClassSlot> findByClassId(Long classId);

  /**
   * Find all slots for a class under a pessimistic write lock ({@code SELECT ... FOR UPDATE}).
   *
   * <p>Use when the caller intends to modify slot capacity or deactivate slots within the same
   * transaction, to prevent concurrent reservation threads from reading stale capacity.
   *
   * @param classId class ID
   * @return list of ClassSlot domain models (locked)
   */
  List<ClassSlot> findByClassIdWithLock(Long classId);

  /**
   * Find a single slot by its ID without acquiring a lock.
   *
   * <p>Used for read-only enrichment (e.g., resolving slotId → classId for reservation display). Do
   * not use when concurrent-write protection is needed — use {@link #findByIdWithLock} instead.
   *
   * @param slotId slot ID
   * @return Optional containing the slot if found
   */
  Optional<ClassSlot> findById(Long slotId);

  /**
   * Find a single slot by its own ID under a pessimistic write lock.
   *
   * <p>Preferred over {@link #findByClassIdWithLock} in reservation creation: locks only the
   * targeted slot row instead of every slot in the class, minimising the lock contention surface.
   *
   * @param slotId the slot ID to lock and load
   * @return the matching ClassSlot, or empty if not found
   */
  Optional<ClassSlot> findByIdWithLock(Long slotId);
}
