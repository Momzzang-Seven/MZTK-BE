package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.level.application.port.out.dto.XpLedgerEntrySlice;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

public interface LoadXpLedgerPort {

  boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  int countByUserIdAndTypeAndEarnedOn(Long userId, XpType type, LocalDate earnedOn);

  XpLedgerEntrySlice loadXpLedgerEntries(Long userId, int page, int size);
}
