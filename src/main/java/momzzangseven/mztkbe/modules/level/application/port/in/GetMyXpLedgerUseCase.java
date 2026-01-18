package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.MyXpLedgerResult;

public interface GetMyXpLedgerUseCase {
  MyXpLedgerResult execute(Long userId, int page, int size);
}
