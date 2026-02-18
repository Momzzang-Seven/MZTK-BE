package momzzangseven.mztkbe.modules.location.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.location.application.port.out.DeleteLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity.LocationEntity;
import momzzangseven.mztkbe.modules.location.infrastructure.repository.LocationJpaRepository;
import org.springframework.stereotype.Component;

/** Location persistence adapter - SaveLocationPort, LoadLocationPort implementation */
@Component
@RequiredArgsConstructor
public class LocationPersistenceAdapter
    implements LoadLocationPort, SaveLocationPort, DeleteLocationPort {

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

  // DeleteLocationPort implementation
  /**
   * Location 삭제 (Hard Delete)
   *
   * <p>⚠️ Verification 레코드는 유지됨 (ON DELETE SET NULL)
   *
   * @param locationId 삭제할 위치 ID
   */
  @Override
  public void deleteById(Long locationId) {
    locationJpaRepository.deleteById(locationId);
  }

  /**
   * 배치 삭제 (User IDs 기반, Soft Deleted만 삭제)
   *
   * <p>Used when WalletUserHardDeleteEventHandler is triggered.
   *
   * @param userIds 삭제할 User ID 목록
   * @return 삭제된 Location 개수
   */
  @Override
  public int deleteByUserIds(List<Long> userIds) {
    return locationJpaRepository.deleteSoftDeletedByUserIds(userIds);
  }
}
