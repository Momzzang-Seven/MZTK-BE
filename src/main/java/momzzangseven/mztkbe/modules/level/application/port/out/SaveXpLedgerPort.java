package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;

public interface SaveXpLedgerPort {
  /**
   * Try to persist a new XP ledger entry.
   *
   * <p>Returns {@code false} when a duplicate idempotency key is detected.
   */
  boolean trySaveXpLedger(XpLedgerEntry entry);
}
