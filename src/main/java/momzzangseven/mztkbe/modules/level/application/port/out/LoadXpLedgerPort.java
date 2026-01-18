package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

public interface LoadXpLedgerPort {

  boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  int countByUserIdAndTypeAndEarnedOn(Long userId, XpType type, LocalDate earnedOn);

  boolean existsByUserIdAndTypeAndEarnedOnBetween(
      Long userId, XpType type, LocalDate startDate, LocalDate endDate);

  int countDistinctEarnedOn(
      Long userId, XpType type, LocalDate startDate, LocalDate endDate);
}
