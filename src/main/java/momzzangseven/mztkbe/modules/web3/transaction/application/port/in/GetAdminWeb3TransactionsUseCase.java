package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.AdminWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.GetAdminWeb3TransactionsCommand;
import org.springframework.data.domain.Page;

public interface GetAdminWeb3TransactionsUseCase {

  Page<AdminWeb3TransactionResult> execute(GetAdminWeb3TransactionsCommand command);
}
