package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDate;
import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

public interface LoadXpLedgerPort {

  boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  int countByUserIdAndTypeAndEarnedOn(Long userId, XpType type, LocalDate earnedOn);

  /**
   * Loads XP ledger entries ordered by createdAt desc.
   *
   * <p>Pagination is performed by {@code page}/{@code size}. Implementations may internally fetch
   * {@code size + 1} items to support {@code hasNext} computation by the application service.
   */
  List<XpLedgerEntry> loadXpLedgerEntries(Long userId, int page, int size);
}
