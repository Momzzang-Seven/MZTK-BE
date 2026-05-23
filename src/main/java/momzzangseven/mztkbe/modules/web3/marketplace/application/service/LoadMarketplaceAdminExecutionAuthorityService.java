package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionAuthorityStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminSignerWalletView;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.LoadMarketplaceAdminExecutionAuthorityUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceAdminRelayerRegistrationPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.VerifyMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

@Slf4j
@RequiredArgsConstructor
public class LoadMarketplaceAdminExecutionAuthorityService
    implements LoadMarketplaceAdminExecutionAuthorityUseCase {

  private final LoadMarketplaceAdminSignerWalletPort loadMarketplaceAdminSignerWalletPort;
  private final VerifyMarketplaceAdminSignerWalletPort verifyMarketplaceAdminSignerWalletPort;
  private final CheckMarketplaceAdminRelayerRegistrationPort
      checkMarketplaceAdminRelayerRegistrationPort;

  @Override
  public MarketplaceAdminExecutionAuthorityStatus execute() {
    return loadMarketplaceAdminSignerWalletPort
        .load()
        .map(this::authority)
        .orElseGet(MarketplaceAdminExecutionAuthorityStatus::serverRelayerOnly);
  }

  private MarketplaceAdminExecutionAuthorityStatus authority(
      MarketplaceAdminSignerWalletView wallet) {
    String signerAddress = normalizedSignerAddress(wallet);
    boolean signerAvailable = wallet.active() && signerAddress != null && signerIsUsable(wallet);
    RelayerRegistration relayerRegistration =
        signerAvailable ? relayerRegistration(signerAddress) : RelayerRegistration.unchecked();
    return new MarketplaceAdminExecutionAuthorityStatus(
        false,
        MarketplaceAdminExecutionAuthorityStatus.SERVER_RELAYER_ONLY,
        signerAvailable,
        signerAvailable ? signerAddress : null,
        relayerRegistration.registered(),
        relayerRegistration.status());
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

  private RelayerRegistration relayerRegistration(String signerAddress) {
    try {
      return checkMarketplaceAdminRelayerRegistrationPort.isRegistered(signerAddress)
          ? RelayerRegistration.registeredStatus()
          : RelayerRegistration.notRegistered();
    } catch (RuntimeException e) {
      log.warn(
          "Failed to validate marketplace admin relayer registration during review preflight: "
              + "signerAddress={}",
          signerAddress,
          e);
      return RelayerRegistration.checkFailed();
    }
  }

  private record RelayerRegistration(boolean registered, String status) {

    private static RelayerRegistration unchecked() {
      return new RelayerRegistration(
          false, MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_UNCHECKED);
    }

    private static RelayerRegistration registeredStatus() {
      return new RelayerRegistration(
          true, MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_REGISTERED);
    }

    private static RelayerRegistration notRegistered() {
      return new RelayerRegistration(
          false, MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_NOT_REGISTERED);
    }

    private static RelayerRegistration checkFailed() {
      return new RelayerRegistration(
          false, MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_CHECK_FAILED);
    }
  }
}
