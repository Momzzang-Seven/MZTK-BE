package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.BulkRequeueAdminWeb3TransactionsUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.BulkRequeueWeb3TransactionsPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class BulkRequeueAdminWeb3TransactionsService
    implements BulkRequeueAdminWeb3TransactionsUseCase {

  private final BulkRequeueWeb3TransactionsPort bulkRequeueWeb3TransactionsPort;

  @Override
  public BulkRequeueAdminWeb3TransactionsResult execute(
      BulkRequeueAdminWeb3TransactionsCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return bulkRequeueWeb3TransactionsPort.requeue(command);
  }
}
