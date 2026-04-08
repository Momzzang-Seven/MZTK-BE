package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionCleanupPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionCleanupPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cleanup expired execution intents and old sponsor usage rows according to retention policy. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class ExecutionIntentCleanupService {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final LoadExecutionCleanupPolicyPort loadExecutionCleanupPolicyPort;

  /**
   * Runs one cleanup batch using configured retention policy.
   *
   * <p>Expired signable intents are first marked as {@code EXPIRED}, then finalized old intents and
   * aged sponsor usage rows are deleted in bounded batch size.
   */
  @Transactional
  public CleanupBatchResult runBatch(Instant now) {
    ExecutionCleanupPolicy cleanupPolicy = loadExecutionCleanupPolicyPort.loadCleanupPolicy();
    ZoneId zone = ZoneId.of(cleanupPolicy.zone());

    LocalDateTime expiredNow = LocalDateTime.ofInstant(now, zone);
    LocalDateTime retainedCutoff = expiredNow.minusDays(cleanupPolicy.retentionDays());
    LocalDate usageCutoff = LocalDate.ofInstant(now, zone).minusDays(cleanupPolicy.retentionDays());

    List<Long> expiredIntentIds =
        executionIntentPersistencePort.findExpiredAwaitingSignatureIds(
            expiredNow, cleanupPolicy.batchSize());
    int expiredIntentCount = expireAwaitingSignatureIntents(expiredIntentIds, expiredNow);

    List<Long> retainedIntentIds =
        executionIntentPersistencePort.findRetainedFinalizedIds(
            retainedCutoff, cleanupPolicy.batchSize());
    int deletedIntentCount =
        retainedIntentIds.isEmpty()
            ? 0
            : Math.toIntExact(executionIntentPersistencePort.deleteByIds(retainedIntentIds));

    List<Long> usageIds =
        sponsorDailyUsagePersistencePort.findUsageIdsForCleanup(
            usageCutoff, cleanupPolicy.batchSize());
    int deletedUsage =
        usageIds.isEmpty()
            ? 0
            : Math.toIntExact(sponsorDailyUsagePersistencePort.deleteByIdIn(usageIds));

    return new CleanupBatchResult(expiredIntentCount, deletedIntentCount, deletedUsage);
  }

  private int expireAwaitingSignatureIntents(
      List<Long> expiredIntentIds, LocalDateTime expiredNow) {
    if (expiredIntentIds.isEmpty()) {
      return 0;
    }

    List<ExecutionIntent> expiredIntents =
        executionIntentPersistencePort.findAllByIdsForUpdate(expiredIntentIds);
    int updatedCount = 0;
    for (ExecutionIntent intent : expiredIntents) {
      if (!intent.getStatus().isSignable()) {
        continue;
      }
      releaseReservedSponsorExposure(intent);
      executionIntentPersistencePort.update(
          intent.expire(
              ErrorCode.EXECUTION_INTENT_EXPIRED.name(),
              ErrorCode.EXECUTION_INTENT_EXPIRED.getMessage(),
              expiredNow));
      updatedCount++;
    }
    return updatedCount;
  }

  private void releaseReservedSponsorExposure(ExecutionIntent intent) {
    if (intent.getReservedSponsorCostWei() == null
        || intent.getReservedSponsorCostWei().signum() <= 0) {
      return;
    }
    sponsorDailyUsagePersistencePort
        .findForUpdate(intent.getRequesterUserId(), intent.resolveSponsorUsageDateKst())
        .ifPresent(
            usage ->
                sponsorDailyUsagePersistencePort.update(
                    usage.release(intent.getReservedSponsorCostWei())));
  }

  /** Batch counters returned by cleanup run. */
  public record CleanupBatchResult(
      int expiredExecutionIntent, int deletedExecutionIntent, int deletedDailyUsage) {
    public int totalDeleted() {
      return expiredExecutionIntent + deletedExecutionIntent + deletedDailyUsage;
    }
  }
}
