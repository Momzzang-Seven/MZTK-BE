package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceInternalExecutionPolicyStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceInternalExecutionPolicyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
@ConditionalOnBean(GetInternalExecutionIssuerPolicyUseCase.class)
public class MarketplaceInternalExecutionPolicyAdapter
    implements LoadMarketplaceInternalExecutionPolicyPort {

  private final GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase;

  @Override
  public MarketplaceInternalExecutionPolicyStatus load() {
    InternalExecutionIssuerPolicyView policy = getInternalExecutionIssuerPolicyUseCase.getPolicy();
    return new MarketplaceInternalExecutionPolicyStatus(
        policy.enabled(),
        policy.marketplaceAdminSettleEnabled(),
        policy.marketplaceAdminRefundEnabled());
  }
}
