package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.treasury;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletNotProvisionedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SignMarketplaceServerSigPort;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAnyExecutionEnabled
public class SignMarketplaceServerSigAdapter implements SignMarketplaceServerSigPort {

  private final Clock appClock;
  private final SignDigestUseCase signDigestUseCase;
  private final LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;
  private final MarketplaceTypedDataDigestBuilder typedDataDigestBuilder;
  private final long chainId;
  private final String marketplaceContractAddress;
  private final String marketplaceEip712DomainName;
  private final String marketplaceEip712DomainVersion;
  private final int signedAtSkewSeconds;
  private final AtomicReference<DomainSeparatorCache> domainSeparatorCache =
      new AtomicReference<>();

  public SignMarketplaceServerSigAdapter(
      Clock appClock,
      SignDigestUseCase signDigestUseCase,
      LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase,
      MarketplaceTypedDataDigestBuilder typedDataDigestBuilder,
      @Value("${web3.chain-id}") long chainId,
      @Value("${web3.escrow.marketplace-contract-address}") String marketplaceContractAddress,
      @Value("${web3.escrow.marketplace-eip712-domain-name:MarketplaceEscrow}")
          String marketplaceEip712DomainName,
      @Value("${web3.escrow.marketplace-eip712-domain-version:1}")
          String marketplaceEip712DomainVersion,
      @Value("${web3.escrow.signed-at-skew-seconds:0}") int signedAtSkewSeconds) {
    this.appClock = appClock;
    this.signDigestUseCase = signDigestUseCase;
    this.loadTreasuryWalletByRoleUseCase = loadTreasuryWalletByRoleUseCase;
    this.typedDataDigestBuilder = typedDataDigestBuilder;
    this.chainId = chainId;
    this.marketplaceContractAddress = marketplaceContractAddress;
    this.marketplaceEip712DomainName = marketplaceEip712DomainName;
    this.marketplaceEip712DomainVersion = marketplaceEip712DomainVersion;
    this.signedAtSkewSeconds = signedAtSkewSeconds;
  }

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
    long signedAt = signingInstant.getEpochSecond() - signedAtSkewSeconds;
    byte[] digest =
        typedDataDigestBuilder.buildDigest(preimage, signedAt, resolveDomainSeparator());

    SignDigestResult signed =
        signDigestUseCase.execute(
            new SignDigestCommand(signer.kmsKeyId(), digest, normalizedWalletAddress));
    return new MarketplaceServerSigResult(signedAt, signed.toCanonical65Bytes(), signingInstant);
  }

  private byte[] resolveDomainSeparator() {
    String verifyingContract = EvmAddress.of(marketplaceContractAddress).value();
    String domainName = marketplaceEip712DomainName;
    String domainVersion = marketplaceEip712DomainVersion;
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
