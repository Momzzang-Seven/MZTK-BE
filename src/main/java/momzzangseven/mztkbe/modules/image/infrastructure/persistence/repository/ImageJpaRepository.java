package momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageJpaRepository extends JpaRepository<ImageEntity, Long> {
  Optional<ImageEntity> findByTmpObjectKey(String tmpObjectKey);

  // findByFinalObjectKey() method is not implemented in this branch.
}
