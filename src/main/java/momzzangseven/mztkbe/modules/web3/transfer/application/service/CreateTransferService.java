package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SubmitExecutionDraftPort;
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
  private final SubmitExecutionDraftPort submitExecutionDraftPort;

  @Override
  public CreateExecutionIntentResult execute(CreateTransferCommand command) {
    return submitExecutionDraftPort.submit(transferExecutionDraftBuilder.build(command));
  }
}
