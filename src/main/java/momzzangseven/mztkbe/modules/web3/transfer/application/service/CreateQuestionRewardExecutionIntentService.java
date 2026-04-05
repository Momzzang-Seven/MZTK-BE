package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateQuestionRewardExecutionIntentUseCase;
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

  private final QuestionRewardExecutionDraftBuilder questionRewardExecutionDraftBuilder;
  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;

  @Override
  public CreateExecutionIntentResult execute(RegisterQuestionRewardIntentCommand command) {
    return createExecutionIntentUseCase.execute(
        new CreateExecutionIntentCommand(questionRewardExecutionDraftBuilder.build(command)));
  }
}
