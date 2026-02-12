package momzzangseven.mztkbe.modules.location.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.location.application.port.out.DeleteLocationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Location Hard Delete Service
 *
 * <p>User Hard Delete 이벤트 수신 시 Soft Deleted Locations를 Hard Delete
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationHardDeleteService {
  private final DeleteLocationPort deleteLocationPort;

  /**
   * Soft Deleted Locations 배치 삭제 (User IDs 기반)
   *
   * <p>UserHardDeleteEventHandler에서 호출
   *
   * <p>deleted_at IS NOT NULL인 Location만 삭제
   *
   * @param userIds 삭제할 User ID 목록
   * @return 삭제된 Location 개수
   */
  @Transactional
  public int deleteByUserIds(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      log.warn("deleteByUserIds called with empty userIds");
      return 0;
    }

    log.info("Deleting soft deleted locations for users: userCount={}", userIds.size());

    int deletedCount = deleteLocationPort.deleteByUserIds(userIds);

    log.info(
        "Deleted soft deleted locations: locationCount={}, userCount={}",
        deletedCount,
        userIds.size());

    return deletedCount;
  }
}
