package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateQuestionRewardExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.BuildQuestionRewardExecutionDraftPort;
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
public class CreateQuestionRewardExecutionIntentService
    implements CreateQuestionRewardExecutionIntentUseCase {

  private final BuildQuestionRewardExecutionDraftPort buildQuestionRewardExecutionDraftPort;
  private final SubmitExecutionDraftPort submitExecutionDraftPort;

  @Override
  public TransferExecutionIntentResult execute(RegisterQuestionRewardIntentCommand command) {
    return submitExecutionDraftPort.submit(buildQuestionRewardExecutionDraftPort.build(command));
  }
}
