package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.AdminWeb3TransactionView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetAdminWeb3TransactionsQuery;
import org.springframework.data.domain.Page;

public interface LoadAdminWeb3TransactionsPort {

  Page<AdminWeb3TransactionView> load(GetAdminWeb3TransactionsQuery query);
}
