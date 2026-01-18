package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;

public interface SaveXpLedgerPort {
  XpLedgerEntry saveXpLedger(XpLedgerEntry entry);
}
