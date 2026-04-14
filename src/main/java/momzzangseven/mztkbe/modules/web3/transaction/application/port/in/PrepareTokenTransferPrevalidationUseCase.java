package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.PrepareTokenTransferPrevalidationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.PrepareTokenTransferPrevalidationResult;

public interface PrepareTokenTransferPrevalidationUseCase {

  PrepareTokenTransferPrevalidationResult execute(PrepareTokenTransferPrevalidationCommand command);
}
