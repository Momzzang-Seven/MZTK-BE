package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;

/** Persistence port for user-based sponsor daily usage rows. */
public interface SponsorDailyUsagePersistencePort {

  Optional<Web3SponsorDailyUsageEntity> findForUpdate(Long userId, LocalDate usageDateKst);

  Web3SponsorDailyUsageEntity save(Web3SponsorDailyUsageEntity entity);

  List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize);

  long deleteByIdIn(List<Long> ids);
}
