package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateTransferUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class CreateTransferService implements CreateTransferUseCase {

  private final TransferExecutionDraftBuilder transferExecutionDraftBuilder;
  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;

  @Override
  public CreateExecutionIntentResult execute(CreateTransferCommand command) {
    return createExecutionIntentUseCase.execute(
        new CreateExecutionIntentCommand(transferExecutionDraftBuilder.build(command)));
  }
}
