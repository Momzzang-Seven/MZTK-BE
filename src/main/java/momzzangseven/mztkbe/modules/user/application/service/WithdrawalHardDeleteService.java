package momzzangseven.mztkbe.modules.user.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.level.application.dto.DeleteUserLevelDataCommand;
import momzzangseven.mztkbe.modules.level.application.port.in.DeleteUserLevelDataUseCase;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalHardDeleteProperties;
import momzzangseven.mztkbe.modules.user.application.port.out.DeleteUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.event.UsersHardDeletedEvent;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalHardDeleteService {

  private final LoadUserPort loadUserPort;
  private final DeleteUserPort deleteUserPort;
  private final ExternalDisconnectTaskPort externalDisconnectTaskPort;
  private final DeleteRefreshTokenPort deleteRefreshTokenPort;
  private final DeleteUserLevelDataUseCase deleteUserLevelDataUseCase;
  private final WithdrawalHardDeleteProperties props;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Run one hard-delete batch.
   *
   * <p>Deletes (in order):
   *
   * <ul>
   *   <li>refresh_tokens by userId (belt & suspenders)
   *   <li>external_disconnect_tasks by userId (avoid orphan retry tasks after hard delete)
   *   <li>users (hard delete)
   * </ul>
   *
   * @return number of deleted users in this batch
   */
  @Transactional
  public int runBatch(LocalDateTime now) {
    int retentionDays = props.getRetentionDays();
    if (retentionDays <= 0) {
      throw new IllegalArgumentException("withdrawal.hard-delete.retention-days must be > 0");
    }

    int batchSize = props.getBatchSize();
    if (batchSize <= 0) {
      throw new IllegalArgumentException("withdrawal.hard-delete.batch-size must be > 0");
    }

    LocalDateTime cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
    List<Long> userIds = loadUserPort.loadUserIdsForDeletion(UserStatus.DELETED, cutoff, batchSize);
    if (userIds.isEmpty()) {
      return 0;
    }

    // Publish user hard-delete event, trigger USER_DELETED wallets to be hard deleted.
    eventPublisher.publishEvent(new UsersHardDeletedEvent(userIds));

    deleteUserLevelDataUseCase.execute(new DeleteUserLevelDataCommand(userIds));

    deleteRefreshTokenPort.deleteByUserIdIn(userIds);

    externalDisconnectTaskPort.deleteByUserIdIn(userIds);
    deleteUserPort.deleteAllByIdInBatch(userIds);

    log.info(
        "Hard deleted users: deletedUsers={}, cutoff={}, retentionDays={}, batchSize={}",
        userIds.size(),
        cutoff,
        retentionDays,
        batchSize);
    return userIds.size();
  }
}
