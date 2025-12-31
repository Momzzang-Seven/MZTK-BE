package momzzangseven.mztkbe.modules.user.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalHardDeleteProperties;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.ExternalDisconnectTaskJpaRepository;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalHardDeleteService {

  private final UserJpaRepository userJpaRepository;
  private final ExternalDisconnectTaskJpaRepository externalDisconnectTaskJpaRepository;
  private final SaveRefreshTokenPort saveRefreshTokenPort;
  private final WithdrawalHardDeleteProperties props;

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
    List<Long> userIds =
        userJpaRepository.findIdsForHardDelete(
            UserStatus.DELETED, cutoff, PageRequest.of(0, batchSize));
    if (userIds.isEmpty()) {
      return 0;
    }

    for (Long userId : userIds) {
      saveRefreshTokenPort.deleteByUserId(userId);
    }

    externalDisconnectTaskJpaRepository.deleteByUserIdIn(userIds);
    userJpaRepository.deleteAllByIdInBatch(userIds);

    log.info(
        "Hard deleted users: deletedUsers={}, cutoff={}, retentionDays={}, batchSize={}",
        userIds.size(),
        cutoff,
        retentionDays,
        batchSize);
    return userIds.size();
  }
}
