package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsResult;

public interface BulkRequeueWeb3TransactionsPort {

  BulkRequeueAdminWeb3TransactionsResult requeue(BulkRequeueAdminWeb3TransactionsCommand command);
}
