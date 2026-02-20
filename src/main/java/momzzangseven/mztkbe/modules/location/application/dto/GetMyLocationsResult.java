package momzzangseven.mztkbe.modules.location.application.dto;

import java.util.List;
import lombok.Builder;

/**
 * Get My Locations Result
 *
 * <p>위치 목록 조회 결과 DTO
 */
@Builder
public record GetMyLocationsResult(List<LocationItem> locations, int totalCount) {
  /**
   * Factory Method: Domain List → Result DTO 변환
   *
   * @param locations 위치 도메인 모델 리스트
   * @return GetMyLocationsResult
   */
  public static GetMyLocationsResult from(List<LocationItem> locations) {
    return GetMyLocationsResult.builder().locations(locations).totalCount(locations.size()).build();
  }

  /**
   * Empty Result Factory Method
   *
   * <p>등록된 위치가 없을 때 사용
   *
   * @return 빈 결과 객체
   */
  public static GetMyLocationsResult empty() {
    return GetMyLocationsResult.builder().locations(List.of()).totalCount(0).build();
  }
}
