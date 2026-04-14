package momzzangseven.mztkbe.modules.web3.eip7702.application.port.in;

import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareTokenTransferExecutionSupportCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareTokenTransferExecutionSupportResult;

public interface PrepareTokenTransferExecutionSupportUseCase {

  PrepareTokenTransferExecutionSupportResult execute(
      PrepareTokenTransferExecutionSupportCommand command);
}
