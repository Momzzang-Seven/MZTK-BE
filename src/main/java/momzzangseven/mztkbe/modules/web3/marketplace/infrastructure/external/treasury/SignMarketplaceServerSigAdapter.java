package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.treasury;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletNotProvisionedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SignMarketplaceServerSigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class SignMarketplaceServerSigAdapter implements SignMarketplaceServerSigPort {

  private final MarketplaceEscrowProperties marketplaceEscrowProperties;
  private final Web3CoreProperties web3CoreProperties;
  private final Clock appClock;
  private final SignDigestUseCase signDigestUseCase;
  private final LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;
  private final MarketplaceTypedDataDigestBuilder typedDataDigestBuilder;
  private final AtomicReference<DomainSeparatorCache> domainSeparatorCache =
      new AtomicReference<>();

  @Override
  public MarketplaceServerSigResult sign(MarketplaceServerSigPreimage preimage) {
    if (preimage == null) {
      throw new Web3InvalidInputException("marketplace server-sig preimage is required");
    }

    TreasuryWalletView signer =
        loadTreasuryWalletByRoleUseCase
            .execute(TreasuryRole.MARKETPLACE_SIGNER)
            .orElseThrow(
                () ->
                    new TreasuryWalletNotProvisionedException(
                        "MARKETPLACE_SIGNER misconfigured: no row"));
    if (signer.status() != TreasuryWalletStatus.ACTIVE) {
      throw new TreasuryWalletNotProvisionedException(
          "MARKETPLACE_SIGNER misconfigured: status=" + signer.status());
    }
    String normalizedWalletAddress = EvmAddress.of(signer.walletAddress()).value();
    Instant signingInstant = appClock.instant();
    long signedAt =
        signingInstant.getEpochSecond() - marketplaceEscrowProperties.getSignedAtSkewSeconds();
    byte[] digest =
        typedDataDigestBuilder.buildDigest(preimage, signedAt, resolveDomainSeparator());

    SignDigestResult signed =
        signDigestUseCase.execute(
            new SignDigestCommand(signer.kmsKeyId(), digest, normalizedWalletAddress));
    return new MarketplaceServerSigResult(signedAt, signed.toCanonical65Bytes(), signingInstant);
  }

  private byte[] resolveDomainSeparator() {
    long chainId = web3CoreProperties.getChainId();
    String verifyingContract =
        EvmAddress.of(marketplaceEscrowProperties.getMarketplaceContractAddress()).value();
    String domainName = marketplaceEscrowProperties.getMarketplaceEip712DomainName();
    String domainVersion = marketplaceEscrowProperties.getMarketplaceEip712DomainVersion();
    DomainSeparatorCacheKey key =
        new DomainSeparatorCacheKey(chainId, verifyingContract, domainName, domainVersion);

    DomainSeparatorCache current = domainSeparatorCache.get();
    if (current != null && current.key().equals(key)) {
      return current.value().clone();
    }

    byte[] computed =
        typedDataDigestBuilder.computeDomainSeparator(
            chainId, verifyingContract, domainName, domainVersion);
    domainSeparatorCache.compareAndSet(current, new DomainSeparatorCache(key, computed));
    return computed.clone();
  }

  private record DomainSeparatorCacheKey(
      long chainId, String verifyingContract, String domainName, String domainVersion) {}

  private record DomainSeparatorCache(DomainSeparatorCacheKey key, byte[] value) {}
}
