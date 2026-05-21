package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceAdminExecutionAuthorityView(
    boolean requiresUserSignature,
    String authorityModel,
    boolean serverSignerAvailable,
    String serverSignerAddress,
    boolean relayerRegistered,
    boolean canEarlySettle,
    boolean canManualRefund) {

  public static final String SERVER_RELAYER_ONLY = "SERVER_RELAYER_ONLY";

  public MarketplaceAdminExecutionAuthorityView {
    if (authorityModel == null || authorityModel.isBlank()) {
      throw new Web3InvalidInputException("authorityModel is required");
    }
    if (requiresUserSignature) {
      throw new Web3InvalidInputException(
          "marketplace admin execution must not require user signature");
    }
  }

  public static MarketplaceAdminExecutionAuthorityView serverRelayerOnly() {
    return new MarketplaceAdminExecutionAuthorityView(
        false, SERVER_RELAYER_ONLY, false, null, false, false, false);
  }
}
