package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.RequeueAdminWeb3TransactionUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.RequeueWeb3TransactionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class RequeueAdminWeb3TransactionService implements RequeueAdminWeb3TransactionUseCase {

  private final RequeueWeb3TransactionPort requeueWeb3TransactionPort;

  @Override
  public RequeueAdminWeb3TransactionResult execute(RequeueAdminWeb3TransactionCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return requeueWeb3TransactionPort.requeue(command);
  }
}
