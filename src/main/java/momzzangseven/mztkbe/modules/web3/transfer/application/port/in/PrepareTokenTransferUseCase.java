package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;

public interface PrepareTokenTransferUseCase {
  PrepareTokenTransferResult execute(PrepareTokenTransferCommand command);
}
