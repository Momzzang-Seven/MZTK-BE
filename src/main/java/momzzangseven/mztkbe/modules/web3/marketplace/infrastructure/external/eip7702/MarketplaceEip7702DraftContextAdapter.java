package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.eip7702;

import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareEip7702AuthorizationUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEip7702DraftContextPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean(PrepareEip7702AuthorizationUseCase.class)
public class MarketplaceEip7702DraftContextAdapter
    implements LoadMarketplaceEip7702DraftContextPort {

  private final PrepareEip7702AuthorizationUseCase prepareEip7702AuthorizationUseCase;
  private final long chainId;
  private final String batchImplAddress;
  private final long authorizationTtlSeconds;

  public MarketplaceEip7702DraftContextAdapter(
      PrepareEip7702AuthorizationUseCase prepareEip7702AuthorizationUseCase,
      @Value("${web3.chain-id}") long chainId,
      @Value("${web3.eip7702.delegation.batch-impl-address}") String batchImplAddress,
      @Value("${web3.eip7702.authorization.ttl-seconds}") long authorizationTtlSeconds) {
    this.prepareEip7702AuthorizationUseCase = prepareEip7702AuthorizationUseCase;
    this.chainId = chainId;
    this.batchImplAddress = batchImplAddress;
    this.authorizationTtlSeconds = authorizationTtlSeconds;
  }

  @Override
  public MarketplaceEip7702DraftContext load(String authorityAddress) {
    String normalizedAuthority = EvmAddress.of(authorityAddress).value();
    String delegateTarget = EvmAddress.of(batchImplAddress).value();
    var authorization =
        prepareEip7702AuthorizationUseCase.execute(
            new PrepareEip7702AuthorizationCommand(chainId, delegateTarget, normalizedAuthority));
    return new MarketplaceEip7702DraftContext(
        chainId,
        delegateTarget,
        authorization.authorityNonce(),
        authorization.authorizationPayloadHash(),
        authorizationTtlSeconds);
  }
}
