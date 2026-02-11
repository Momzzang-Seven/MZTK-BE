package momzzangseven.mztkbe.modules.location.infrastructure.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.location.application.service.LocationHardDeleteService;
import momzzangseven.mztkbe.modules.user.domain.event.UsersHardDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for user hard deletion
 *
 * <p>User 모듈의 스케줄러가 User를 Hard Delete할 때 발행하는 이벤트 수신
 *
 * <p>REQUIRES_NEW 트랜잭션을 사용하여 User 삭제와 독립적으로 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserHardDeleteEventHandler {
  private final LocationHardDeleteService locationHardDeleteService;

  /**
   * Handle users hard deleted event
   *
   * <p>User 모듈에서 배치 삭제한 User ID 목록을 받아 Location도 Hard Delete
   *
   * @param event event containing user IDs that were hard-deleted
   */
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleUsersHardDeleted(UsersHardDeletedEvent event) {
    List<Long> userIds = event.userIds();

    if (userIds == null || userIds.isEmpty()) {
      log.warn("Received UsersHardDeletedEvent with empty userIds");
      return;
    }

    log.info(
        "Handling user hard delete event: cascade deleting soft deleted locations for userCount={}",
        userIds.size());

    try {
      int deletedCount = locationHardDeleteService.deleteByUserIds(userIds);

      log.info(
          "Successfully cascade deleted soft deleted locations: locationCount={}, userCount={}",
          deletedCount,
          userIds.size());

    } catch (Exception e) {
      // 예외를 잡아서 로깅만 하고 전파하지 않음
      // 다른 이벤트 리스너가 계속 실행될 수 있도록
      log.error(
          "Failed to cascade delete soft deleted locations for users: userCount={}",
          userIds.size(),
          e);
    }
  }
}
