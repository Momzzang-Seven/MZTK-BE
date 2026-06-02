package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionResult;

public interface RequeueWeb3TransactionUseCase {

  RequeueWeb3TransactionResult execute(RequeueWeb3TransactionCommand command);
}
