package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTransactionIntentCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTransactionIntentResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTxIntentCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.CreateLevelUpRewardTransactionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.SaveTransactionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateLevelUpRewardTransactionIntentService
    implements CreateLevelUpRewardTransactionIntentUseCase {

  private final SaveTransactionPort saveTransactionPort;

  @Override
  public CreateLevelUpRewardTransactionIntentResult execute(
      CreateLevelUpRewardTransactionIntentCommand command) {
    var transaction =
        saveTransactionPort.saveLevelUpRewardIntent(
            new CreateLevelUpRewardTxIntentCommand(
                command.userId(),
                command.referenceId(),
                command.idempotencyKey(),
                command.fromAddress(),
                command.toAddress(),
                command.amountWei()));

    return new CreateLevelUpRewardTransactionIntentResult(
        transaction.getStatus().name(), transaction.getTxHash(), transaction.getFailureReason());
  }
}
