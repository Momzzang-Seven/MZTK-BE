package momzzangseven.mztkbe.modules.location.infrastructure.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Location JPA Repository */
@Repository
public interface LocationJpaRepository extends JpaRepository<LocationEntity, Long> {
  /** Get List of Location by userId in order registered_at desc */
  List<LocationEntity> findByUserIdOrderByRegisteredAtDesc(Long userId);

  /**
   * Soft Deleted Location 배치 삭제 (User IDs 기반)
   *
   * <p>⚠️ deleted_at IS NOT NULL인 Location만 삭제 (which is in Soft-deleted status)
   *
   * @param userIds 삭제할 User ID 목록
   * @return 삭제된 개수
   */
  @Modifying
  @Query("DELETE FROM LocationEntity l WHERE l.userId IN :userIds")
  int deleteSoftDeletedByUserIds(@Param("userIds") List<Long> userIds);
}
