package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalResult;

public interface ReplayWalletRegistrationApprovalUseCase {

  ReplayWalletRegistrationApprovalResult execute(ReplayWalletRegistrationApprovalCommand command);
}
