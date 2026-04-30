package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ClassSlotEntity}.
 *
 * <p>Provides two query modes:
 *
 * <ul>
 *   <li>{@link #findByClassId} — read-only lookup used for conflict detection and display
 *   <li>{@link #findByClassIdWithLock} — {@code SELECT ... FOR UPDATE} used when mutating slots
 *       (capacity change, deactivation) to prevent concurrent reservation races
 * </ul>
 */
@Repository
public interface ClassSlotJpaRepository extends JpaRepository<ClassSlotEntity, Long> {

  /**
   * Read-only slot lookup by class ID (no lock).
   *
   * @param classId class ID
   * @return all slot entities for the class (active and inactive)
   */
  List<ClassSlotEntity> findByClassId(Long classId);

  /**
   * Pessimistic-write lock slot lookup used before updating slot capacity or deactivating slots.
   *
   * <p>Acquires {@code SELECT ... FOR UPDATE}, blocking concurrent reservation threads from
   * over-committing capacity until this transaction completes.
   *
   * @param classId class ID
   * @return all slot entities for the class (active and inactive), locked
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM ClassSlotEntity s WHERE s.classId = :classId")
  List<ClassSlotEntity> findByClassIdWithLock(@Param("classId") Long classId);

  /**
   * Pessimistic-write lock lookup by slot ID.
   *
   * <p>Used by the reservation module when a user creates a reservation to prevent concurrent
   * over-booking of the same slot.
   *
   * @param slotId slot ID
   * @return Optional containing the entity under a write lock
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM ClassSlotEntity s WHERE s.id = :slotId")
  java.util.Optional<ClassSlotEntity> findByIdWithLock(@Param("slotId") Long slotId);
}
