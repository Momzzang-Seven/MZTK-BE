package momzzangseven.mztkbe.modules.marketplace.classes.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;

/**
 * Output port for loading class time-slots from persistence.
 *
 * <p>Provides two access modes to separate the read-only display path from the write-path that
 * needs to prevent concurrent reservation over-commits.
 *
 * <ul>
 *   <li>{@link #findByClassId} — no lock; used for conflict-checking during class registration and
 *       for display.
 *   <li>{@link #findByClassIdWithLock} — {@code SELECT ... FOR UPDATE}; used in {@link
 *       momzzangseven.mztkbe.modules.marketplace.application.service.UpdateClassService} when
 *       modifying slot capacity to prevent a concurrent reservation committing between the read and
 *       the write.
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
   * Find a single slot by its own ID under a pessimistic write lock.
   *
   * <p>Preferred over {@link #findByClassIdWithLock} in reservation creation: locks only the
   * targeted slot row instead of every slot in the class, minimising the lock contention surface.
   *
   * @param slotId the slot ID to lock and load
   * @return the matching ClassSlot, or empty if not found
   */
  java.util.Optional<ClassSlot> findByIdWithLock(Long slotId);
}
