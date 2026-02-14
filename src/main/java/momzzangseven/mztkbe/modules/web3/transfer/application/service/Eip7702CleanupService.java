package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3SponsorDailyUsageJpaRepository;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
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

  private final Web3TransferPrepareJpaRepository prepareRepository;
  private final Web3SponsorDailyUsageJpaRepository sponsorDailyUsageRepository;
  private final Eip7702Properties eip7702Properties;

  @Transactional
  public CleanupBatchResult runBatch(Instant now) {
    ZoneId zone = ZoneId.of(eip7702Properties.getCleanup().getZone());
    int retentionDays = eip7702Properties.getCleanup().getRetentionDays();
    int batchSize = eip7702Properties.getCleanup().getBatchSize();

    LocalDateTime prepareCutoff = LocalDateTime.ofInstant(now, zone).minusDays(retentionDays);
    LocalDate usageCutoff = LocalDate.ofInstant(now, zone).minusDays(retentionDays);

    List<String> prepareIds =
        prepareRepository.findPrepareIdsForCleanup(prepareCutoff, PageRequest.of(0, batchSize));
    List<Long> usageIds =
        sponsorDailyUsageRepository.findUsageIdsForCleanup(
            usageCutoff, PageRequest.of(0, batchSize));

    int deletedPrepare =
        prepareIds.isEmpty()
            ? 0
            : Math.toIntExact(prepareRepository.deleteByPrepareIdIn(prepareIds));
    int deletedUsage =
        usageIds.isEmpty()
            ? 0
            : Math.toIntExact(sponsorDailyUsageRepository.deleteByIdIn(usageIds));

    return new CleanupBatchResult(deletedPrepare, deletedUsage);
  }

  public record CleanupBatchResult(int deletedPrepare, int deletedDailyUsage) {
    public int totalDeleted() {
      return deletedPrepare + deletedDailyUsage;
    }
  }
}
