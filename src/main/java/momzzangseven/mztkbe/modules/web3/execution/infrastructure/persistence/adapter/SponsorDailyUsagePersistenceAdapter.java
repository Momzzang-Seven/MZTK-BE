package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3SponsorDailyUsageJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component("executionSponsorDailyUsagePersistenceAdapter")
@RequiredArgsConstructor
public class SponsorDailyUsagePersistenceAdapter implements SponsorDailyUsagePersistencePort {

  private final Web3SponsorDailyUsageJpaRepository repository;
  private final Clock appClock;

  @Override
  public Optional<SponsorDailyUsage> find(Long userId, LocalDate usageDateKst) {
    return repository.findByUserIdAndUsageDateKst(userId, usageDateKst).map(this::toDomain);
  }

  @Override
  public Optional<SponsorDailyUsage> findForUpdate(Long userId, LocalDate usageDateKst) {
    return repository.findForUpdate(userId, usageDateKst).map(this::toDomain);
  }

  @Override
  public SponsorDailyUsage getOrCreateForUpdate(Long userId, LocalDate usageDateKst) {
    Optional<SponsorDailyUsage> existing = findForUpdate(userId, usageDateKst);
    if (existing.isPresent()) {
      return existing.get();
    }

    try {
      create(SponsorDailyUsage.create(userId, usageDateKst));
    } catch (DataIntegrityViolationException ignored) {
      // Another transaction created the same day row first. Re-read under lock below.
    }

    return findForUpdate(userId, usageDateKst)
        .orElseThrow(() -> new IllegalStateException("failed to get or create sponsor usage row"));
  }

  @Override
  public SponsorDailyUsage create(SponsorDailyUsage usage) {
    if (usage.getId() != null) {
      throw new Web3InvalidInputException("create requires id to be null");
    }
    LocalDateTime now = LocalDateTime.now(appClock);
    return toDomain(repository.save(toEntity(usage, now)));
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
    entity.setUpdatedAt(LocalDateTime.now(appClock));
    return toDomain(repository.save(entity));
  }

  @Override
  public List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize) {
    return repository.findUsageIdsForCleanup(cutoffDate, PageRequest.of(0, batchSize));
  }

  @Override
  public long deleteByIdIn(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0L;
    }
    repository.deleteByIdIn(ids);
    return ids.size();
  }

  private Web3SponsorDailyUsageEntity toEntity(SponsorDailyUsage usage, LocalDateTime now) {
    Web3SponsorDailyUsageEntity entity = Web3SponsorDailyUsageEntity.builder().build();
    merge(usage, entity);
    entity.setCreatedAt(usage.getCreatedAt() != null ? usage.getCreatedAt() : now);
    entity.setUpdatedAt(usage.getUpdatedAt() != null ? usage.getUpdatedAt() : now);
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
