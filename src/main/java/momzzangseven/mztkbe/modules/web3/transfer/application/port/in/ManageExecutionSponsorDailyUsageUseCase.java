package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.ExecutionSponsorDailyUsageRecord;

public interface ManageExecutionSponsorDailyUsageUseCase {

  Optional<ExecutionSponsorDailyUsageRecord> find(Long userId, LocalDate usageDateKst);

  Optional<ExecutionSponsorDailyUsageRecord> findForUpdate(Long userId, LocalDate usageDateKst);

  ExecutionSponsorDailyUsageRecord getOrCreateForUpdate(Long userId, LocalDate usageDateKst);

  ExecutionSponsorDailyUsageRecord create(ExecutionSponsorDailyUsageRecord usage);

  ExecutionSponsorDailyUsageRecord update(ExecutionSponsorDailyUsageRecord usage);

  List<Long> findUsageIdsForCleanup(LocalDate cutoffDate, int batchSize);

  long deleteByIdIn(List<Long> ids);
}
