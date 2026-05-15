package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ValidateWalletRegistrationApprovalExecutionCommand;

public interface ValidateWalletRegistrationApprovalExecutionUseCase {

  void execute(ValidateWalletRegistrationApprovalExecutionCommand command);
}
