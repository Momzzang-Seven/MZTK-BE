package momzzangseven.mztkbe.modules.account.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteUserLevelDataPort;
import momzzangseven.mztkbe.modules.account.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.account.application.port.out.HardDeleteUsersPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadHardDeletePolicyPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.event.UsersHardDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalHardDeleteService {

  private final LoadUserAccountPort loadUserAccountPort;
  private final HardDeleteUsersPort hardDeleteUsersPort;
  private final DeleteUserAccountPort deleteUserAccountPort;
  private final ExternalDisconnectTaskPort externalDisconnectTaskPort;
  private final DeleteRefreshTokenPort deleteRefreshTokenPort;
  private final DeleteUserLevelDataPort deleteUserLevelDataPort;
  private final LoadHardDeletePolicyPort policyPort;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Run one hard-delete batch.
   *
   * @return number of deleted users in this batch
   */
  @Transactional
  public int runBatch(Instant now) {
    int retentionDays = policyPort.getRetentionDays();
    if (retentionDays <= 0) {
      throw new IllegalArgumentException("withdrawal.hard-delete.retention-days must be > 0");
    }

    int batchSize = policyPort.getBatchSize();
    if (batchSize <= 0) {
      throw new IllegalArgumentException("withdrawal.hard-delete.batch-size must be > 0");
    }

    Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
    List<Long> userIds = loadUserAccountPort.findUserIdsForHardDeletion(cutoff, batchSize);
    if (userIds.isEmpty()) {
      return 0;
    }

    // Publish user hard-delete event, trigger USER_DELETED wallets to be hard deleted.
    eventPublisher.publishEvent(new UsersHardDeletedEvent(userIds));

    deleteUserLevelDataPort.deleteByUserIds(userIds);

    deleteRefreshTokenPort.deleteByUserIdIn(userIds);

    externalDisconnectTaskPort.deleteByUserIdIn(userIds);
    deleteUserAccountPort.deleteByUserIdIn(userIds);
    hardDeleteUsersPort.hardDeleteUsers(userIds);

    log.info(
        "Hard deleted users: deletedUsers={}, cutoff={}, retentionDays={}, batchSize={}",
        userIds.size(),
        cutoff,
        retentionDays,
        batchSize);
    return userIds.size();
  }
}
