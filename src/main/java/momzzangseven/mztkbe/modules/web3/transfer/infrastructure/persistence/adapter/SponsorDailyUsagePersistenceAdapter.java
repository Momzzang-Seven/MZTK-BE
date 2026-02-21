package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.SponsorDailyUsageRecord;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3SponsorDailyUsageJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SponsorDailyUsagePersistenceAdapter implements SponsorDailyUsagePersistencePort {

  private final Web3SponsorDailyUsageJpaRepository repository;

  @Override
  public Optional<SponsorDailyUsageRecord> findForUpdate(Long userId, LocalDate usageDateKst) {
    return repository.findForUpdate(userId, usageDateKst).map(this::toRecord);
  }

  @Override
  public SponsorDailyUsageRecord save(SponsorDailyUsageRecord record) {
    Web3SponsorDailyUsageEntity entity =
        record.getId() == null
            ? repository
                .findForUpdate(record.getUserId(), record.getUsageDateKst())
                .orElseGet(Web3SponsorDailyUsageEntity.builder()::build)
            : repository
                .findById(record.getId())
                .orElseGet(Web3SponsorDailyUsageEntity.builder()::build);

    entity.setUserId(record.getUserId());
    entity.setUsageDateKst(record.getUsageDateKst());
    entity.setEstimatedCostWei(record.getEstimatedCostWei());

    return toRecord(repository.save(entity));
  }

  @Override
  public List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize) {
    return repository.findUsageIdsForCleanup(cutoffDate, PageRequest.of(0, batchSize));
  }

  @Override
  public long deleteByIdIn(List<Long> ids) {
    return repository.deleteByIdIn(ids);
  }

  private SponsorDailyUsageRecord toRecord(Web3SponsorDailyUsageEntity entity) {
    return SponsorDailyUsageRecord.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .usageDateKst(entity.getUsageDateKst())
        .estimatedCostWei(entity.getEstimatedCostWei())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
