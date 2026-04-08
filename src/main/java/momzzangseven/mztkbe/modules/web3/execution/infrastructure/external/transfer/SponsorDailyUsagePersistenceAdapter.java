package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.transfer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.ExecutionSponsorDailyUsageRecord;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.ManageExecutionSponsorDailyUsageUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("executionSponsorDailyUsagePersistenceAdapter")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class SponsorDailyUsagePersistenceAdapter implements SponsorDailyUsagePersistencePort {

  private final ManageExecutionSponsorDailyUsageUseCase manageExecutionSponsorDailyUsageUseCase;

  @Override
  public Optional<SponsorDailyUsage> find(Long userId, LocalDate usageDateKst) {
    return manageExecutionSponsorDailyUsageUseCase.find(userId, usageDateKst).map(this::toDomain);
  }

  @Override
  public Optional<SponsorDailyUsage> findForUpdate(Long userId, LocalDate usageDateKst) {
    return manageExecutionSponsorDailyUsageUseCase
        .findForUpdate(userId, usageDateKst)
        .map(this::toDomain);
  }

  @Override
  public SponsorDailyUsage getOrCreateForUpdate(Long userId, LocalDate usageDateKst) {
    return toDomain(
        manageExecutionSponsorDailyUsageUseCase.getOrCreateForUpdate(userId, usageDateKst));
  }

  @Override
  public SponsorDailyUsage create(SponsorDailyUsage usage) {
    return toDomain(manageExecutionSponsorDailyUsageUseCase.create(toRecord(usage)));
  }

  @Override
  public SponsorDailyUsage update(SponsorDailyUsage usage) {
    return toDomain(manageExecutionSponsorDailyUsageUseCase.update(toRecord(usage)));
  }

  @Override
  public List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize) {
    return manageExecutionSponsorDailyUsageUseCase.findUsageIdsForCleanup(cutoffDate, batchSize);
  }

  @Override
  public long deleteByIdIn(List<Long> ids) {
    return manageExecutionSponsorDailyUsageUseCase.deleteByIdIn(ids);
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
}
