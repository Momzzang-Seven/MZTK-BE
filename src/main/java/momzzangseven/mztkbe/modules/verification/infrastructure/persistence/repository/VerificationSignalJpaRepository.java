package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationSignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationSignalJpaRepository
    extends JpaRepository<VerificationSignalEntity, Long> {
  List<VerificationSignalEntity> findByVerificationRequestIdOrderByCreatedAtAsc(
      Long verificationRequestId);
}
