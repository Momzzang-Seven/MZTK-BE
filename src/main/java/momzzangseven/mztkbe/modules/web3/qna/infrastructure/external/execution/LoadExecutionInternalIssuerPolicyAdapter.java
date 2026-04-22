package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnQnaAdminEnabled
public class LoadExecutionInternalIssuerPolicyAdapter
    implements LoadExecutionInternalIssuerPolicyPort {

  private final GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase;

  @Override
  public ExecutionInternalIssuerPolicy loadPolicy() {
    InternalExecutionIssuerPolicyView policy = getInternalExecutionIssuerPolicyUseCase.getPolicy();
    return new ExecutionInternalIssuerPolicy(
        policy.enabled(), policy.qnaAdminSettleEnabled(), policy.qnaAdminRefundEnabled());
  }
}
