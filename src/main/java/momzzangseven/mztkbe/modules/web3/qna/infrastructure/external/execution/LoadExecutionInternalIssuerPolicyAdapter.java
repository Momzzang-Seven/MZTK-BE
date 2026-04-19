package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class LoadExecutionInternalIssuerPolicyAdapter
    implements LoadExecutionInternalIssuerPolicyPort {

  private final GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase;

  @Override
  public ExecutionInternalIssuerPolicy loadPolicy() {
    InternalExecutionIssuerPolicyView policy = getInternalExecutionIssuerPolicyUseCase.getPolicy();
    return new ExecutionInternalIssuerPolicy(policy.enabled(), policy.qnaAdminSettleEnabled());
  }
}
