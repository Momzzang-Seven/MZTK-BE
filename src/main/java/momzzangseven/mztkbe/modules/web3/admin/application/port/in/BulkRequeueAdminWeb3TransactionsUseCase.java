package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsResult;

public interface BulkRequeueAdminWeb3TransactionsUseCase {

  BulkRequeueAdminWeb3TransactionsResult execute(BulkRequeueAdminWeb3TransactionsCommand command);
}
