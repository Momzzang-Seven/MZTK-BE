package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.ExecutionSponsorDailyUsageRecord;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.ManageExecutionSponsorDailyUsageUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.SponsorDailyUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ManageExecutionSponsorDailyUsageService
    implements ManageExecutionSponsorDailyUsageUseCase {

  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;

  @Override
  @Transactional(readOnly = true)
  public Optional<ExecutionSponsorDailyUsageRecord> find(Long userId, LocalDate usageDateKst) {
    return sponsorDailyUsagePersistencePort.find(userId, usageDateKst).map(this::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ExecutionSponsorDailyUsageRecord> findForUpdate(
      Long userId, LocalDate usageDateKst) {
    return sponsorDailyUsagePersistencePort.findForUpdate(userId, usageDateKst).map(this::toRecord);
  }

  @Override
  public ExecutionSponsorDailyUsageRecord getOrCreateForUpdate(
      Long userId, LocalDate usageDateKst) {
    Optional<ExecutionSponsorDailyUsageRecord> existing = findForUpdate(userId, usageDateKst);
    if (existing.isPresent()) {
      return existing.get();
    }

    try {
      create(toRecord(SponsorDailyUsage.create(userId, usageDateKst)));
    } catch (DataIntegrityViolationException ignored) {
      // Another transaction created the same day row first. Re-read under lock below.
    }

    return findForUpdate(userId, usageDateKst)
        .orElseThrow(() -> new IllegalStateException("failed to get or create sponsor usage row"));
  }

  @Override
  public ExecutionSponsorDailyUsageRecord create(ExecutionSponsorDailyUsageRecord usage) {
    return toRecord(sponsorDailyUsagePersistencePort.create(toDomain(usage)));
  }

  @Override
  public ExecutionSponsorDailyUsageRecord update(ExecutionSponsorDailyUsageRecord usage) {
    return toRecord(sponsorDailyUsagePersistencePort.update(toDomain(usage)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize) {
    return sponsorDailyUsagePersistencePort.findUsageIdsForCleanup(cutoffDate, batchSize);
  }

  @Override
  public long deleteByIdIn(List<Long> ids) {
    return sponsorDailyUsagePersistencePort.deleteByIdIn(ids);
  }

  private ExecutionSponsorDailyUsageRecord toRecord(SponsorDailyUsage usage) {
    return new ExecutionSponsorDailyUsageRecord(
        usage.getId(),
        usage.getUserId(),
        usage.getUsageDateKst(),
        usage.getReservedCostWei(),
        usage.getConsumedCostWei(),
        usage.getCreatedAt(),
        usage.getUpdatedAt());
  }

  private SponsorDailyUsage toDomain(ExecutionSponsorDailyUsageRecord usage) {
    return SponsorDailyUsage.builder()
        .id(usage.id())
        .userId(usage.userId())
        .usageDateKst(usage.usageDateKst())
        .reservedCostWei(usage.reservedCostWei())
        .consumedCostWei(usage.consumedCostWei())
        .createdAt(usage.createdAt())
        .updatedAt(usage.updatedAt())
        .build();
  }
}
