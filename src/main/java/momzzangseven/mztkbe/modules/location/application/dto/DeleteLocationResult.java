package momzzangseven.mztkbe.modules.location.application.dto;

import java.time.Instant;
import lombok.Builder;
import momzzangseven.mztkbe.modules.location.domain.model.Location;

/**
 * Delete Location Result
 *
 * @param locationId
 * @param locationName
 * @param deletedAt
 */
@Builder
public record DeleteLocationResult(Long locationId, String locationName, Instant deletedAt) {
  /**
   * Factory Method: Location → DeleteLocationResult
   *
   * @param location Deleted Location
   * @return DeleteLocationResult
   */
  public static DeleteLocationResult from(Location location) {
    return DeleteLocationResult.builder()
        .locationId(location.getId())
        .locationName(location.getLocationName())
        // location 도메인 오브젝트의 deletedAt 필드는 user의 soft delete 시에만 유효합니다.
        // 사용자가 직접 location 삭제를 요청했을 때에는 곧바로 DB에서 삭제되기 때문에 deletedAt 필드가 필요 없게 됩니다.
        // 따라서 now를 삭제시간으로 알려줍니다.
        .deletedAt(Instant.now())
        .build();
  }
}
