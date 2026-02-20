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
 * <p>REQUIRED 트랜잭션을 사용하여 User 삭제와 동일한 트랜잭션에서 실행
 *
 * <p>Location 삭제 실패 시 User 삭제도 롤백되어 데이터 정합성을 보장합니다. User 삭제 스케줄러가 다음 실행 시 재시도합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationUserHardDeleteEventHandler {
  private final LocationHardDeleteService locationHardDeleteService;

  /**
   * Handle users hard deleted event
   *
   * <p>User 모듈에서 배치 삭제한 User ID 목록을 받아 Location도 Hard Delete
   *
   * <p>User와 Location의 원자적 삭제를 보장하기 위해 같은 트랜잭션에서 실행됩니다.
   *
   * @param event event containing user IDs that were hard-deleted
   * @throws Exception e Location 삭제 실패 시 User 삭제도 롤백되도록 예외 전파
   */
  @EventListener
  @Transactional(propagation = Propagation.REQUIRED)
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
      // log만 남긴 후 예외를 throw. -> Roll back -> User hard deletion도 roll back
      log.error(
          """
                      CRITICAL: Failed to delete locations.
                      User deletion will be rolled back and retried tomorrow.
                      UserCount: {}""",
          userIds.size(),
          e);
      throw e;
    }
  }
}
