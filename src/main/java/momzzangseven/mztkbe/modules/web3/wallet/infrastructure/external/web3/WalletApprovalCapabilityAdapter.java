package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalCapabilityPort;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletApprovalProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class WalletApprovalCapabilityAdapter implements LoadWalletApprovalCapabilityPort {

  private final WalletApprovalProperties properties;

  @Override
  public WalletApprovalCapability load() {
    if (!Boolean.TRUE.equals(properties.getEnabled())) {
      return WalletApprovalCapability.unavailable("wallet approval flow is disabled");
    }
    try {
      EvmAddress.of(properties.getTokenContractAddress());
      EvmAddress.of(properties.getQnaEscrowSpenderAddress());
      EvmAddress.of(properties.getMarketplaceEscrowSpenderAddress());
    } catch (RuntimeException e) {
      return WalletApprovalCapability.unavailable("wallet approval configuration is invalid");
    }
    return WalletApprovalCapability.enabled();
  }
}
