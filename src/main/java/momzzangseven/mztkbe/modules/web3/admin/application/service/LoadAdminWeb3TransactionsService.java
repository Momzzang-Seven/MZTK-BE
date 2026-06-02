package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.AdminWeb3TransactionView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetAdminWeb3TransactionsQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.LoadAdminWeb3TransactionsUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.LoadAdminWeb3TransactionsPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class LoadAdminWeb3TransactionsService implements LoadAdminWeb3TransactionsUseCase {

  private final LoadAdminWeb3TransactionsPort loadAdminWeb3TransactionsPort;

  @Override
  public Page<AdminWeb3TransactionView> execute(GetAdminWeb3TransactionsQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
    return loadAdminWeb3TransactionsPort.load(query);
  }
}
