package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.SponsorDailyUsageRecord;

/** Persistence port for user-based sponsor daily usage rows. */
public interface SponsorDailyUsagePersistencePort {

  Optional<SponsorDailyUsageRecord> findForUpdate(Long userId, LocalDate usageDateKst);

  SponsorDailyUsageRecord save(SponsorDailyUsageRecord record);

  List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize);

  long deleteByIdIn(List<Long> ids);
}
