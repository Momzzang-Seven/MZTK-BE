package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.eip7702;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEip7702DraftContextPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean({Eip7702ChainPort.class, Eip7702AuthorizationPort.class})
public class MarketplaceEip7702DraftContextAdapter
    implements LoadMarketplaceEip7702DraftContextPort {

  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final Eip7702Properties eip7702Properties;
  private final Web3CoreProperties web3CoreProperties;

  @Override
  public MarketplaceEip7702DraftContext load(String authorityAddress) {
    String normalizedAuthority = EvmAddress.of(authorityAddress).value();
    String delegateTarget =
        EvmAddress.of(eip7702Properties.getDelegation().getBatchImplAddress()).value();
    long authorityNonce =
        eip7702ChainPort.loadPendingAccountNonce(normalizedAuthority).longValueExact();
    String authorizationPayloadHash =
        eip7702AuthorizationPort.buildSigningHashHex(
            web3CoreProperties.getChainId(), delegateTarget, BigInteger.valueOf(authorityNonce));
    return new MarketplaceEip7702DraftContext(
        web3CoreProperties.getChainId(),
        delegateTarget,
        authorityNonce,
        authorizationPayloadHash,
        eip7702Properties.getAuthorization().getTtlSeconds());
  }
}
