package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.AdminWeb3TransactionView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetAdminWeb3TransactionsQuery;
import org.springframework.data.domain.Page;

public interface LoadAdminWeb3TransactionsUseCase {

  Page<AdminWeb3TransactionView> execute(GetAdminWeb3TransactionsQuery query);
}
