package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleConfigurationValidationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3InternalExecutionPolicyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ValidateMarketplaceWeb3AutoSettleConfigurationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceWeb3InternalExecutionPolicyPort;

@RequiredArgsConstructor
public class ValidateMarketplaceWeb3AutoSettleConfigurationService
    implements ValidateMarketplaceWeb3AutoSettleConfigurationUseCase {

  private final LoadMarketplaceWeb3InternalExecutionPolicyPort loadPolicyPort;
  private final LoadMarketplaceAdminExecutionAuthorityPort loadAuthorityPort;

  @Override
  public MarketplaceWeb3AutoSettleConfigurationValidationResult validate() {
    MarketplaceWeb3InternalExecutionPolicyStatus policy =
        loadPolicyPort.loadInternalExecutionPolicy();
    if (!policy.enabled()) {
      throw new IllegalStateException(
          "web3.marketplace.admin.auto-settle.enabled=true requires web3.execution.internal.enabled=true");
    }
    if (!policy.marketplaceAdminSettleEnabled()) {
      throw new IllegalStateException(
          "web3.marketplace.admin.auto-settle.enabled=true requires web3.execution.internal.action-policy to enable MARKETPLACE_ADMIN_SETTLE");
    }

    MarketplaceAdminExecutionAuthorityView authority = loadAuthorityPort.load();
    if (!authority.serverSignerAvailable() || authority.serverSignerAddress() == null) {
      return MarketplaceWeb3AutoSettleConfigurationValidationResult.authorityWarning(
          "Marketplace admin execution signer is unavailable at startup");
    }
    if (authority.relayerRegistrationCheckFailed()) {
      return MarketplaceWeb3AutoSettleConfigurationValidationResult.authorityWarning(
          "Marketplace admin execution failed to validate current server signer relayer registration at startup");
    }
    if (!authority.relayerRegistered()) {
      return MarketplaceWeb3AutoSettleConfigurationValidationResult.authorityWarning(
          "Marketplace admin execution signer is not registered as relayer at startup: signerAddress="
              + authority.serverSignerAddress());
    }
    return MarketplaceWeb3AutoSettleConfigurationValidationResult.ok();
  }
}
