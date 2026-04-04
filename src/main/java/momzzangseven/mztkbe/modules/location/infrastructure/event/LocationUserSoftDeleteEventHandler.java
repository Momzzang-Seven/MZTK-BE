package momzzangseven.mztkbe.modules.location.infrastructure.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.domain.event.UserSoftDeletedEvent;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for user soft-deletion
 *
 * <p>회원 탈퇴 시 해당 유저의 모든 Location을 Soft Delete 처리
 *
 * <p>User 모듈에서 발행한 UserSoftDeletedEvent를 수신
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationUserSoftDeleteEventHandler {
  private final LoadLocationPort loadLocationPort;
  private final SaveLocationPort saveLocationPort;

  /**
   * Handle user soft-deleted event
   *
   * <p>soft-delete된 사용자와 연결된 위치의 deletedAt 필드가 설정됩니다.
   *
   * <p>User 모듈의 회원 탈퇴 이벤트 수신 시 호출
   *
   * @param event user soft deleted event
   */
  @EventListener
  @Transactional
  public void handleUserSoftDeleted(UserSoftDeletedEvent event) {
    Long userId = event.userId();

    if (userId == null) {
      log.warn("Received UserSoftDeletedEvent with null userId");
      return;
    }

    log.info("Handling user soft deleted event: userId={}", userId);

    // 1. 해당 유저의 활성 Location 조회
    List<Location> locations = loadLocationPort.findByUserId(userId);

    if (locations.isEmpty()) {
      log.info("No active locations found for userId={}", userId);
      return;
    }

    // 2. 각 Location을 Soft Delete 처리
    int markedCount = 0;
    for (Location location : locations) {
      Location deletedLocation = location.markAsDeleted();
      saveLocationPort.save(deletedLocation);
      markedCount++;
    }

    log.info("Marked locations as soft deleted: userId={}, count={}", userId, markedCount);
  }
}
