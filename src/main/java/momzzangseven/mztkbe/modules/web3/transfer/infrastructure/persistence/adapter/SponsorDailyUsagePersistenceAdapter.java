package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3SponsorDailyUsageJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SponsorDailyUsagePersistenceAdapter implements SponsorDailyUsagePersistencePort {

  private final Web3SponsorDailyUsageJpaRepository repository;

  @Override
  public Optional<SponsorDailyUsage> findForUpdate(Long userId, LocalDate usageDateKst) {
    return repository.findForUpdate(userId, usageDateKst).map(this::toDomain);
  }

  @Override
  public SponsorDailyUsage create(SponsorDailyUsage usage) {
    if (usage.getId() != null) {
      throw new Web3InvalidInputException("create requires id to be null");
    }
    return toDomain(repository.save(toEntity(usage)));
  }

  @Override
  public SponsorDailyUsage update(SponsorDailyUsage usage) {
    if (usage.getId() == null) {
      throw new Web3InvalidInputException("update requires id");
    }
    Web3SponsorDailyUsageEntity entity =
        repository
            .findById(usage.getId())
            .orElseThrow(() -> new Web3InvalidInputException("sponsor usage not found"));
    merge(usage, entity);
    return toDomain(repository.save(entity));
  }

  @Override
  public List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize) {
    return repository.findUsageIdsForCleanup(cutoffDate, PageRequest.of(0, batchSize));
  }

  @Override
  public long deleteByIdIn(List<Long> ids) {
    return repository.deleteByIdIn(ids);
  }

  private Web3SponsorDailyUsageEntity toEntity(SponsorDailyUsage usage) {
    Web3SponsorDailyUsageEntity entity = Web3SponsorDailyUsageEntity.builder().build();
    merge(usage, entity);
    return entity;
  }

  private void merge(SponsorDailyUsage usage, Web3SponsorDailyUsageEntity entity) {
    entity.setUserId(usage.getUserId());
    entity.setUsageDateKst(usage.getUsageDateKst());
    entity.setReservedCostWei(usage.getReservedCostWei());
    entity.setConsumedCostWei(usage.getConsumedCostWei());
  }

  private SponsorDailyUsage toDomain(Web3SponsorDailyUsageEntity entity) {
    return SponsorDailyUsage.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .usageDateKst(entity.getUsageDateKst())
        .reservedCostWei(entity.getReservedCostWei())
        .consumedCostWei(entity.getConsumedCostWei())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
