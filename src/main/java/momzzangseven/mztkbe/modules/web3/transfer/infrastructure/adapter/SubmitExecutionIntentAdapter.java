package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SubmitExecutionDraftPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubmitExecutionIntentAdapter implements SubmitExecutionDraftPort {

  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;

  @Override
  public CreateExecutionIntentResult submit(ExecutionDraft draft) {
    return createExecutionIntentUseCase.execute(new CreateExecutionIntentCommand(draft));
  }
}
