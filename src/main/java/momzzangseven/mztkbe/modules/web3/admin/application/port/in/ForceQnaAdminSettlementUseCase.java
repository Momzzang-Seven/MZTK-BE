package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementResult;

public interface ForceQnaAdminSettlementUseCase {

  ForceQnaAdminSettlementResult execute(ForceQnaAdminSettlementCommand command);
}
