package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminSignerWalletView;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.VerifyMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceEscrowProperties;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3.MarketplaceContractCallSupport;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnMarketplaceAdminEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
@ConditionalOnBean({
  LoadMarketplaceAdminSignerWalletPort.class,
  VerifyMarketplaceAdminSignerWalletPort.class
})
public class MarketplaceAdminExecutionAuthorityAdapter
    implements LoadMarketplaceAdminExecutionAuthorityPort {

  private final LoadMarketplaceAdminSignerWalletPort loadMarketplaceAdminSignerWalletPort;
  private final VerifyMarketplaceAdminSignerWalletPort verifyMarketplaceAdminSignerWalletPort;
  private final MarketplaceContractCallSupport marketplaceContractCallSupport;
  private final MarketplaceEscrowProperties marketplaceEscrowProperties;

  @Override
  public MarketplaceAdminExecutionAuthorityView load() {
    return loadMarketplaceAdminSignerWalletPort
        .load()
        .map(this::authority)
        .orElseGet(MarketplaceAdminExecutionAuthorityView::serverRelayerOnly);
  }

  private MarketplaceAdminExecutionAuthorityView authority(
      MarketplaceAdminSignerWalletView wallet) {
    String signerAddress = normalizedSignerAddress(wallet);
    boolean signerAvailable = wallet.active() && signerAddress != null && signerIsUsable(wallet);
    boolean relayerRegistered = signerAvailable && relayerRegistered(signerAddress);
    return new MarketplaceAdminExecutionAuthorityView(
        false,
        MarketplaceAdminExecutionAuthorityView.SERVER_RELAYER_ONLY,
        signerAvailable,
        signerAvailable ? signerAddress : null,
        relayerRegistered,
        false,
        false);
  }

  private String normalizedSignerAddress(MarketplaceAdminSignerWalletView wallet) {
    if (wallet.walletAddress() == null || wallet.walletAddress().isBlank()) {
      return null;
    }
    try {
      return EvmAddress.of(wallet.walletAddress()).value();
    } catch (RuntimeException e) {
      log.warn(
          "Marketplace admin signer address is invalid during review preflight: walletAlias={}",
          wallet.walletAlias(),
          e);
      return null;
    }
  }

  private boolean signerIsUsable(MarketplaceAdminSignerWalletView wallet) {
    try {
      verifyMarketplaceAdminSignerWalletPort.verify(wallet.walletAlias());
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Marketplace admin signer is unavailable during review preflight: walletAlias={}",
          wallet.walletAlias(),
          e);
      return false;
    }
  }

  private boolean relayerRegistered(String signerAddress) {
    try {
      return marketplaceContractCallSupport.isRelayerRegistered(
          marketplaceEscrowProperties.getMarketplaceContractAddress(), signerAddress);
    } catch (RuntimeException e) {
      log.warn(
          "Failed to validate marketplace admin relayer registration during review preflight: signerAddress={}",
          signerAddress,
          e);
      return false;
    }
  }
}
