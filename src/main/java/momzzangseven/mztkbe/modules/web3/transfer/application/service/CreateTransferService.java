package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.BuildTransferExecutionDraftPort;
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
/** Transfer use case entry that builds and submits execution draft in one step. */
public class CreateTransferService implements CreateTransferUseCase {

  private final BuildTransferExecutionDraftPort buildTransferExecutionDraftPort;
  private final SubmitExecutionDraftPort submitExecutionDraftPort;

  /** Creates transfer execution intent and returns client-facing sign request contract. */
  @Override
  public TransferExecutionIntentResult execute(CreateTransferCommand command) {
    return submitExecutionDraftPort.submit(buildTransferExecutionDraftPort.build(command));
  }
}
