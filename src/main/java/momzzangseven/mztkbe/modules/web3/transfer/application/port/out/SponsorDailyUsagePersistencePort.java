package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.SponsorDailyUsage;

/** Persistence port for user-based sponsor daily usage rows. */
public interface SponsorDailyUsagePersistencePort {

  Optional<SponsorDailyUsage> findForUpdate(Long userId, LocalDate usageDateKst);

  SponsorDailyUsage create(SponsorDailyUsage usage);

  SponsorDailyUsage update(SponsorDailyUsage usage);

  List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize);

  long deleteByIdIn(List<Long> ids);
}
