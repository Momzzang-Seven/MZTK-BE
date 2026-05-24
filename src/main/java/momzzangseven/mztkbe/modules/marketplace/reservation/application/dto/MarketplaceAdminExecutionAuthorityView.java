package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceAdminExecutionAuthorityView(
    boolean requiresUserSignature,
    String authorityModel,
    boolean serverSignerAvailable,
    String serverSignerAddress,
    boolean relayerRegistered,
    String relayerRegistrationStatus,
    boolean canEarlySettle,
    boolean canManualRefund) {

  public static final String SERVER_RELAYER_ONLY = "SERVER_RELAYER_ONLY";
  public static final String RELAYER_REGISTRATION_UNCHECKED = "UNCHECKED";
  public static final String RELAYER_REGISTRATION_REGISTERED = "REGISTERED";
  public static final String RELAYER_REGISTRATION_NOT_REGISTERED = "NOT_REGISTERED";
  public static final String RELAYER_REGISTRATION_CHECK_FAILED = "CHECK_FAILED";

  public MarketplaceAdminExecutionAuthorityView(
      boolean requiresUserSignature,
      String authorityModel,
      boolean serverSignerAvailable,
      String serverSignerAddress,
      boolean relayerRegistered,
      boolean canEarlySettle,
      boolean canManualRefund) {
    this(
        requiresUserSignature,
        authorityModel,
        serverSignerAvailable,
        serverSignerAddress,
        relayerRegistered,
        defaultRelayerRegistrationStatus(serverSignerAvailable, relayerRegistered),
        canEarlySettle,
        canManualRefund);
  }

  public MarketplaceAdminExecutionAuthorityView {
    if (authorityModel == null || authorityModel.isBlank()) {
      throw new Web3InvalidInputException("authorityModel is required");
    }
    if (relayerRegistrationStatus == null || relayerRegistrationStatus.isBlank()) {
      throw new Web3InvalidInputException("relayerRegistrationStatus is required");
    }
    if (!isKnownRelayerRegistrationStatus(relayerRegistrationStatus)) {
      throw new Web3InvalidInputException("relayerRegistrationStatus is invalid");
    }
    if (requiresUserSignature) {
      throw new Web3InvalidInputException(
          "marketplace admin execution must not require user signature");
    }
    if (relayerRegistered && !RELAYER_REGISTRATION_REGISTERED.equals(relayerRegistrationStatus)) {
      throw new Web3InvalidInputException(
          "relayerRegistrationStatus must be REGISTERED when relayerRegistered is true");
    }
    if (!relayerRegistered && RELAYER_REGISTRATION_REGISTERED.equals(relayerRegistrationStatus)) {
      throw new Web3InvalidInputException(
          "relayerRegistered must be true when relayerRegistrationStatus is REGISTERED");
    }
    if (!serverSignerAvailable
        && !RELAYER_REGISTRATION_UNCHECKED.equals(relayerRegistrationStatus)) {
      throw new Web3InvalidInputException(
          "relayerRegistrationStatus must be UNCHECKED when server signer is unavailable");
    }
    if (serverSignerAvailable && RELAYER_REGISTRATION_UNCHECKED.equals(relayerRegistrationStatus)) {
      throw new Web3InvalidInputException(
          "relayerRegistrationStatus must be checked when server signer is available");
    }
  }

  public static MarketplaceAdminExecutionAuthorityView serverRelayerOnly() {
    return new MarketplaceAdminExecutionAuthorityView(
        false, SERVER_RELAYER_ONLY, false, null, false, false, false);
  }

  public MarketplaceAdminExecutionAuthorityView withOperatorAuthority(
      boolean canEarlySettle, boolean canManualRefund) {
    return new MarketplaceAdminExecutionAuthorityView(
        requiresUserSignature,
        authorityModel,
        serverSignerAvailable,
        serverSignerAddress,
        relayerRegistered,
        relayerRegistrationStatus,
        canEarlySettle,
        canManualRefund);
  }

  public boolean relayerRegistrationCheckFailed() {
    return RELAYER_REGISTRATION_CHECK_FAILED.equals(relayerRegistrationStatus);
  }

  private static String defaultRelayerRegistrationStatus(
      boolean serverSignerAvailable, boolean relayerRegistered) {
    if (!serverSignerAvailable) {
      return RELAYER_REGISTRATION_UNCHECKED;
    }
    return relayerRegistered
        ? RELAYER_REGISTRATION_REGISTERED
        : RELAYER_REGISTRATION_NOT_REGISTERED;
  }

  private static boolean isKnownRelayerRegistrationStatus(String value) {
    return RELAYER_REGISTRATION_UNCHECKED.equals(value)
        || RELAYER_REGISTRATION_REGISTERED.equals(value)
        || RELAYER_REGISTRATION_NOT_REGISTERED.equals(value)
        || RELAYER_REGISTRATION_CHECK_FAILED.equals(value);
  }
}
