package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

/** Outbound port for XP ledger. */
public interface XpLedgerPort {
  boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  int countByUserIdAndTypeAndEarnedOn(Long userId, XpType type, LocalDate earnedOn);

  Optional<XpLedgerEntry> findLatestByUserIdAndTypeAndEarnedOn(
      Long userId, XpType type, LocalDate earnedOn);

  /**
   * Loads XP ledger entries ordered by createdAt desc.
   *
   * <p>Pagination is performed by {@code page}/{@code size}. Implementations may internally fetch
   * {@code size + 1} items to support {@code hasNext} computation by the application service.
   */
  List<XpLedgerEntry> loadXpLedgerEntries(Long userId, int page, int size);

  /**
   * Try to persist a new XP ledger entry.
   *
   * <p>Returns {@code false} when a duplicate idempotency key is detected.
   */
  boolean trySaveXpLedger(XpLedgerEntry entry);
}
