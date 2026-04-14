package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferSucceededCommand;

public interface HandleTransferSucceededUseCase {

  void execute(HandleTransferSucceededCommand command);
}
