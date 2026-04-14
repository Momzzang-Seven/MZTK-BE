package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;

public interface SponsorDailyUsagePersistencePort {

  Optional<SponsorDailyUsage> find(Long userId, LocalDate usageDateKst);

  Optional<SponsorDailyUsage> findForUpdate(Long userId, LocalDate usageDateKst);

  SponsorDailyUsage getOrCreateForUpdate(Long userId, LocalDate usageDateKst);

  SponsorDailyUsage create(SponsorDailyUsage usage);

  SponsorDailyUsage update(SponsorDailyUsage usage);

  List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize);

  long deleteByIdIn(List<Long> ids);
}
