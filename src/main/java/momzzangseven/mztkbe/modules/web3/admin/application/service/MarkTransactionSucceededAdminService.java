package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededAdminCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.MarkTransactionSucceededAdminUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class MarkTransactionSucceededAdminService implements MarkTransactionSucceededAdminUseCase {

  private final MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  @Override
  public MarkTransactionSucceededResult execute(MarkTransactionSucceededAdminCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return markTransactionSucceededUseCase.execute(
        new MarkTransactionSucceededCommand(
            command.operatorId(),
            command.transactionId(),
            command.txHash(),
            command.explorerUrl(),
            command.reason(),
            command.evidence()));
  }
}
