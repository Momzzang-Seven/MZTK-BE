package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SubmitExecutionDraftPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(CreateExecutionIntentUseCase.class)
@RequiredArgsConstructor
/** Adapter that bridges transfer draft submission to shared execution create use case. */
public class SubmitExecutionIntentAdapter implements SubmitExecutionDraftPort {

  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;

  /**
   * Delegates draft submission to execution module without transfer-module coupling to service
   * impl.
   */
  @Override
  public CreateExecutionIntentResult submit(ExecutionDraft draft) {
    return createExecutionIntentUseCase.execute(new CreateExecutionIntentCommand(draft));
  }
}
