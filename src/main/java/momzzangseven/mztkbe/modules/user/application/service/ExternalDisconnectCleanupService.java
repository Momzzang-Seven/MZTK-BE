package momzzangseven.mztkbe.modules.user.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalExternalDisconnectCleanupProperties;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalHardDeleteProperties;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.ExternalDisconnectTaskJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ExternalDisconnectCleanupService {

  private final ExternalDisconnectTaskJpaRepository repository;
  private final WithdrawalExternalDisconnectCleanupProperties cleanupProps;
  private final WithdrawalHardDeleteProperties hardDeleteProps;

  /**
   * Delete old SUCCESS (and long-retained FAILED) rows by {@code updatedAt}.
   *
   * @return number of deleted rows
   */
  public int cleanup(LocalDateTime now) {
    int deleted = 0;

    int hardDeleteRetentionDays = hardDeleteProps.getRetentionDays();

    int successRetentionDays =
        clampRetentionDays(cleanupProps.getSuccessRetentionDays(), hardDeleteRetentionDays);
    LocalDateTime successCutoff = now.minus(successRetentionDays, ChronoUnit.DAYS);
    deleted +=
        repository.deleteByStatusAndUpdatedAtBefore(
            ExternalDisconnectStatus.SUCCESS, successCutoff);

    if (cleanupProps.getFailedRetentionDays() > 0) {
      int failedRetentionDays =
          clampRetentionDays(cleanupProps.getFailedRetentionDays(), hardDeleteRetentionDays);
      LocalDateTime failedCutoff = now.minus(failedRetentionDays, ChronoUnit.DAYS);
      deleted +=
          repository.deleteByStatusAndUpdatedAtBefore(
              ExternalDisconnectStatus.FAILED, failedCutoff);
    }

    if (deleted > 0) {
      log.info(
          "External disconnect cleanup deleted rows: deleted={}, successRetentionDays={}, "
              + "failedRetentionDays={}",
          deleted,
          successRetentionDays,
          cleanupProps.getFailedRetentionDays());
    }

    return deleted;
  }

  private static int clampRetentionDays(int configuredDays, int hardDeleteDays) {
    if (configuredDays <= 0) {
      throw new IllegalArgumentException("retentionDays must be > 0");
    }
    if (hardDeleteDays <= 0) {
      throw new IllegalArgumentException("hardDelete retentionDays must be > 0");
    }
    return Math.min(configuredDays, hardDeleteDays);
  }
}
