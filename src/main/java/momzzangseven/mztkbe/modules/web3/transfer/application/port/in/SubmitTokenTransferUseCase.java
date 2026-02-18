package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferResult;

public interface SubmitTokenTransferUseCase {
  SubmitTokenTransferResult execute(SubmitTokenTransferCommand command);
}
