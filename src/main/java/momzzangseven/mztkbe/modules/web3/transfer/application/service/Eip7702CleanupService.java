package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferPreparePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cleanup old EIP-7702 transfer rows according to retention policy. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class Eip7702CleanupService {

  private final TransferPreparePersistencePort transferPreparePersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;

  @Transactional
  public CleanupBatchResult runBatch(Instant now) {
    TransferRuntimeConfig runtimeConfig = loadTransferRuntimeConfigPort.load();
    ZoneId zone = ZoneId.of(runtimeConfig.cleanupZone());
    int retentionDays = runtimeConfig.cleanupRetentionDays();
    int batchSize = runtimeConfig.cleanupBatchSize();

    LocalDateTime prepareCutoff = LocalDateTime.ofInstant(now, zone).minusDays(retentionDays);
    LocalDate usageCutoff = LocalDate.ofInstant(now, zone).minusDays(retentionDays);

    List<String> prepareIds =
        transferPreparePersistencePort.findPrepareIdsForCleanup(prepareCutoff, batchSize);
    List<Long> usageIds =
        sponsorDailyUsagePersistencePort.findUsageIdsForCleanup(usageCutoff, batchSize);

    int deletedPrepare =
        prepareIds.isEmpty()
            ? 0
            : Math.toIntExact(transferPreparePersistencePort.deleteByPrepareIdIn(prepareIds));
    int deletedUsage =
        usageIds.isEmpty()
            ? 0
            : Math.toIntExact(sponsorDailyUsagePersistencePort.deleteByIdIn(usageIds));

    return new CleanupBatchResult(deletedPrepare, deletedUsage);
  }

  public record CleanupBatchResult(int deletedPrepare, int deletedDailyUsage) {
    public int totalDeleted() {
      return deletedPrepare + deletedDailyUsage;
    }
  }
}
