package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3InternalExecutionPolicyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceWeb3InternalExecutionPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.execution.internal", name = "enabled", havingValue = "true")
public class MarketplaceWeb3InternalExecutionPolicyAdapter
    implements LoadMarketplaceWeb3InternalExecutionPolicyPort {

  private final GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase;

  @Override
  public MarketplaceWeb3InternalExecutionPolicyStatus loadInternalExecutionPolicy() {
    InternalExecutionIssuerPolicyView policy = getInternalExecutionIssuerPolicyUseCase.getPolicy();
    return new MarketplaceWeb3InternalExecutionPolicyStatus(
        policy.enabled(),
        policy.marketplaceAdminSettleEnabled(),
        policy.marketplaceAdminRefundEnabled());
  }
}
