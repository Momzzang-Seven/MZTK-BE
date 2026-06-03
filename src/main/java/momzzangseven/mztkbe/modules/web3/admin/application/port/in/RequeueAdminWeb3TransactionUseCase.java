package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionResult;

public interface RequeueAdminWeb3TransactionUseCase {

  RequeueAdminWeb3TransactionResult execute(RequeueAdminWeb3TransactionCommand command);
}
