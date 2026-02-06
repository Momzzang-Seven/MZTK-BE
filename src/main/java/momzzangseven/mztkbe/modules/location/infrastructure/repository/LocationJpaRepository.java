package momzzangseven.mztkbe.modules.location.infrastructure.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Location JPA Repository */
@Repository
public interface LocationJpaRepository extends JpaRepository<LocationEntity, Long> {
  /** Get List of Location by userId in order registered_at desc */
  List<LocationEntity> findByUserIdOrderByRegisteredAtDesc(Long userId);
}
