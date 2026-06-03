package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionsResult;

public interface BulkRequeueWeb3TransactionsUseCase {

  BulkRequeueWeb3TransactionsResult execute(BulkRequeueWeb3TransactionsCommand command);
}
