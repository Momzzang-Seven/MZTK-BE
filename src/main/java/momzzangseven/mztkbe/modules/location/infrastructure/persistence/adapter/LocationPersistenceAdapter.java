package momzzangseven.mztkbe.modules.location.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity.LocationEntity;
import momzzangseven.mztkbe.modules.location.infrastructure.repository.LocationJpaRepository;
import org.springframework.stereotype.Component;

/** Location persistence adapter - SaveLocationPort, LoadLocationPort implementation */
@Component
@RequiredArgsConstructor
public class LocationPersistenceAdapter implements LoadLocationPort, SaveLocationPort {

  private final LocationJpaRepository locationJpaRepository;

  // SaveLocationPort 구현
  @Override
  public Location save(Location location) {
    LocationEntity entity = LocationEntity.from(location);
    LocationEntity savedEntity = locationJpaRepository.save(entity);
    return savedEntity.toDomain();
  }

  // LoadLocationPort 구현
  @Override
  public Optional<Location> findByLocationId(Long id) {
    return locationJpaRepository.findById(id).map(LocationEntity::toDomain);
  }

  @Override
  public List<Location> findByUserId(Long userId) {
    return locationJpaRepository.findByUserIdOrderByRegisteredAtDesc(userId).stream()
        .map(LocationEntity::toDomain)
        .toList();
  }
}
