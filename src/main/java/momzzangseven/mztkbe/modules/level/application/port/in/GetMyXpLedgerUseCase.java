package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.GetMyXpLedgerResult;

public interface GetMyXpLedgerUseCase {
  GetMyXpLedgerResult execute(Long userId, int page, int size);
}
