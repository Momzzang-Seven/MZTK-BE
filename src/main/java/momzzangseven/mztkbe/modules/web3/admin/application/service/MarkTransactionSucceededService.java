package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service("web3AdminMarkTransactionSucceededService")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class MarkTransactionSucceededService implements MarkTransactionSucceededUseCase {

  private final momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      markTransactionSucceededUseCase;

  @Override
  public MarkTransactionSucceededResult execute(MarkTransactionSucceededCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return markTransactionSucceededUseCase.execute(
        new momzzangseven.mztkbe.modules.web3.transaction.application.dto
            .MarkTransactionSucceededCommand(
            command.operatorId(),
            command.transactionId(),
            command.txHash(),
            command.explorerUrl(),
            command.reason(),
            command.evidence()));
  }
}
