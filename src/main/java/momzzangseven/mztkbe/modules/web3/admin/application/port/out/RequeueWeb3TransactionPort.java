package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionResult;

public interface RequeueWeb3TransactionPort {

  RequeueAdminWeb3TransactionResult requeue(RequeueAdminWeb3TransactionCommand command);
}
