package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.command.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.result.PrepareTokenTransferResult;

public interface PrepareTokenTransferUseCase {
  PrepareTokenTransferResult execute(PrepareTokenTransferCommand command);
}
