package momzzangseven.mztkbe.modules.location.application.port.in;

import momzzangseven.mztkbe.modules.location.application.dto.GetMyLocationsResult;

/**
 * Get My Locations Use Case
 *
 * <p>사용자가 등록한 위치 목록 조회 유스케이스
 */
public interface GetMyLocationsUseCase {
  /**
   * 사용자가 등록한 모든 위치 목록 조회
   *
   * @param userId 사용자 ID
   * @return GetMyLocationsResult 위치 목록 및 개수
   */
  GetMyLocationsResult execute(Long userId);
}
