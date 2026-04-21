package momzzangseven.mztkbe.modules.marketplace.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.domain.model.ClassSlot;

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
}
