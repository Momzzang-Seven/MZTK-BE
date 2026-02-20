package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3SponsorDailyUsageJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SponsorDailyUsagePersistenceAdapter implements SponsorDailyUsagePersistencePort {

  private final Web3SponsorDailyUsageJpaRepository repository;

  @Override
  public Optional<Web3SponsorDailyUsageEntity> findForUpdate(Long userId, LocalDate usageDateKst) {
    return repository.findForUpdate(userId, usageDateKst);
  }

  @Override
  public Web3SponsorDailyUsageEntity save(Web3SponsorDailyUsageEntity entity) {
    return repository.save(entity);
  }

  @Override
  public List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize) {
    return repository.findUsageIdsForCleanup(cutoffDate, PageRequest.of(0, batchSize));
  }

  @Override
  public long deleteByIdIn(List<Long> ids) {
    return repository.deleteByIdIn(ids);
  }
}
