package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalTerminatedCommand;

public interface MarkWalletRegistrationApprovalTerminatedUseCase {

  void execute(MarkWalletRegistrationApprovalTerminatedCommand command);
}
