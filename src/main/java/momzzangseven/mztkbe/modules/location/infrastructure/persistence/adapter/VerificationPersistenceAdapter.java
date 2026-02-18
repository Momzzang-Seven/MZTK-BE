package momzzangseven.mztkbe.modules.location.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveVerificationPort;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity.LocationVerificationEntity;
import momzzangseven.mztkbe.modules.location.infrastructure.repository.LocationVerificationJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Verification Persistence Adapter
 *
 * <p>Location verification record save adapter.
 */
@Component
@RequiredArgsConstructor
public class VerificationPersistenceAdapter implements SaveVerificationPort {
  private final LocationVerificationJpaRepository repository;

  @Override
  public LocationVerification save(LocationVerification verification) {
    LocationVerificationEntity entity = LocationVerificationEntity.fromDomain(verification);
    LocationVerificationEntity saved = repository.save(entity);
    return saved.toDomain();
  }
}
