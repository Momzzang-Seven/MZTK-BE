package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.command.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.result.SubmitTokenTransferResult;

public interface SubmitTokenTransferUseCase {
  SubmitTokenTransferResult execute(SubmitTokenTransferCommand command);
}
