package momzzangseven.mztkbe.modules.location.infrastructure.repository;

import momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity.LocationVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Location Verification Jpa Repository */
@Repository
public interface LocationVerificationJpaRepository
    extends JpaRepository<LocationVerificationEntity, Long> {
  // Basic CRUD methods only
}
