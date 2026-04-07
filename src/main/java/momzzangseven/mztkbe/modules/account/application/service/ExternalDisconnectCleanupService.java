package momzzangseven.mztkbe.modules.account.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadExternalDisconnectCleanupPolicyPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadHardDeletePolicyPort;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ExternalDisconnectCleanupService {

  private final ExternalDisconnectTaskPort externalDisconnectTaskPort;
  private final LoadExternalDisconnectCleanupPolicyPort cleanupPolicyPort;
  private final LoadHardDeletePolicyPort hardDeletePolicyPort;

  /**
   * Delete old SUCCESS (and long-retained FAILED) rows by {@code updatedAt}.
   *
   * @return number of deleted rows
   */
  public int cleanup(Instant now) {
    int deleted = 0;

    int hardDeleteRetentionDays = hardDeletePolicyPort.getRetentionDays();

    int successRetentionDays =
        clampRetentionDays(cleanupPolicyPort.getSuccessRetentionDays(), hardDeleteRetentionDays);
    Instant successCutoff = now.minus(successRetentionDays, ChronoUnit.DAYS);
    deleted +=
        externalDisconnectTaskPort.deleteByStatusAndUpdatedAtBefore(
            ExternalDisconnectStatus.SUCCESS, successCutoff);

    if (cleanupPolicyPort.getFailedRetentionDays() > 0) {
      int failedRetentionDays =
          clampRetentionDays(cleanupPolicyPort.getFailedRetentionDays(), hardDeleteRetentionDays);
      Instant failedCutoff = now.minus(failedRetentionDays, ChronoUnit.DAYS);
      deleted +=
          externalDisconnectTaskPort.deleteByStatusAndUpdatedAtBefore(
              ExternalDisconnectStatus.FAILED, failedCutoff);
    }

    if (deleted > 0) {
      log.info(
          "External disconnect cleanup deleted rows: deleted={}, successRetentionDays={}, "
              + "failedRetentionDays={}",
          deleted,
          successRetentionDays,
          cleanupPolicyPort.getFailedRetentionDays());
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
