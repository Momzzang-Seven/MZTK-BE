package momzzangseven.mztkbe.modules.location.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.location.application.dto.GetMyLocationsResult;
import momzzangseven.mztkbe.modules.location.application.dto.LocationItem;
import momzzangseven.mztkbe.modules.location.application.port.in.GetMyLocationsUseCase;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Get My Locations Service
 *
 * <p>사용자가 등록한 위치 목록 조회 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetMyLocationsService implements GetMyLocationsUseCase {
  private final LoadLocationPort loadLocationPort;

  @Override
  public GetMyLocationsResult execute(Long userId) {
    log.info("Fetching locations for user: {}", userId);

    // 1. Load locations from database
    List<Location> locations = loadLocationPort.findByUserId(userId);

    // 2. Domain → Application DTO 변환
    List<LocationItem> locationItems = locations.stream().map(LocationItem::from).toList();

    log.info("Found {} locations for user: {}", locationItems.size(), userId);

    // 3. Result 객체 생성 및 반환
    return GetMyLocationsResult.from(locationItems);
  }
}
