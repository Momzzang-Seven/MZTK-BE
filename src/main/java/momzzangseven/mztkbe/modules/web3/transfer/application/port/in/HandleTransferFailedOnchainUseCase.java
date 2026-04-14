package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferFailedOnchainCommand;

public interface HandleTransferFailedOnchainUseCase {

  void execute(HandleTransferFailedOnchainCommand command);
}
