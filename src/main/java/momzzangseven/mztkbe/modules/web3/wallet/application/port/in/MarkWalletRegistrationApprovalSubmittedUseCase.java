package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalSubmittedCommand;

public interface MarkWalletRegistrationApprovalSubmittedUseCase {

  void execute(MarkWalletRegistrationApprovalSubmittedCommand command);
}
