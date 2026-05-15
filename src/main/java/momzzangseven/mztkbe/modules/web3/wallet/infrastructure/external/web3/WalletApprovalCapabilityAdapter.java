package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSponsorPolicy;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalCapabilityPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletApprovalProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class WalletApprovalCapabilityAdapter implements LoadWalletApprovalCapabilityPort {

  private static final BigInteger WEI_SCALE = BigInteger.TEN.pow(18);
  private static final BigInteger WEI_PER_GWEI = BigInteger.valueOf(1_000_000_000L);
  private static final BigInteger RESERVATION_NUMERATOR = BigInteger.valueOf(12);
  private static final BigInteger RESERVATION_DENOMINATOR = BigInteger.TEN;

  private final WalletApprovalProperties properties;
  private final LoadWalletApprovalSponsorPolicyPort loadSponsorPolicyPort;

  @Override
  public WalletApprovalCapability load() {
    if (!Boolean.TRUE.equals(properties.getEnabled())) {
      return WalletApprovalCapability.unavailable("wallet approval flow is disabled");
    }
    try {
      EvmAddress.of(properties.getDelegationBatchImplAddress());
      EvmAddress.of(properties.getTokenContractAddress());
      EvmAddress.of(properties.getQnaEscrowSpenderAddress());
      EvmAddress.of(properties.getMarketplaceEscrowSpenderAddress());
      WalletApprovalSponsorPolicy sponsorPolicy = loadSponsorPolicyPort.load();
      if (!sponsorPolicy.enabled()) {
        return WalletApprovalCapability.unavailable("wallet approval sponsor is disabled");
      }
      BigInteger reservedCostWei = estimateReservedCostWei(sponsorPolicy);
      if (reservedCostWei.compareTo(ethToWei(sponsorPolicy.perTxCapEth())) > 0
          || reservedCostWei.compareTo(ethToWei(sponsorPolicy.perDayUserCapEth())) > 0) {
        return WalletApprovalCapability.unavailable(
            "wallet approval sponsor policy is insufficient");
      }
    } catch (RuntimeException e) {
      return WalletApprovalCapability.unavailable("wallet approval configuration is invalid");
    }
    return WalletApprovalCapability.enabled();
  }

  private BigInteger estimateReservedCostWei(WalletApprovalSponsorPolicy sponsorPolicy) {
    BigInteger gasLimit = BigInteger.valueOf(sponsorPolicy.maxGasLimit());
    BigInteger maxFeePerGas =
        BigInteger.valueOf(sponsorPolicy.maxMaxFeeGwei()).multiply(WEI_PER_GWEI);
    return gasLimit
        .multiply(maxFeePerGas)
        .multiply(RESERVATION_NUMERATOR)
        .divide(RESERVATION_DENOMINATOR);
  }

  private BigInteger ethToWei(BigDecimal eth) {
    return eth.multiply(new BigDecimal(WEI_SCALE)).toBigIntegerExact();
  }
}
