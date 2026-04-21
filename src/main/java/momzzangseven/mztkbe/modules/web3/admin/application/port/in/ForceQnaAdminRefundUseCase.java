package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundResult;

public interface ForceQnaAdminRefundUseCase {

  ForceQnaAdminRefundResult execute(ForceQnaAdminRefundCommand command);
}
