package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceAdminExecutionAuthorityStatus(
    boolean requiresUserSignature,
    String authorityModel,
    boolean serverSignerAvailable,
    String serverSignerAddress,
    boolean relayerRegistered,
    String relayerRegistrationStatus) {

  public static final String SERVER_RELAYER_ONLY = "SERVER_RELAYER_ONLY";
  public static final String RELAYER_REGISTRATION_UNCHECKED = "UNCHECKED";
  public static final String RELAYER_REGISTRATION_REGISTERED = "REGISTERED";
  public static final String RELAYER_REGISTRATION_NOT_REGISTERED = "NOT_REGISTERED";
  public static final String RELAYER_REGISTRATION_CHECK_FAILED = "CHECK_FAILED";

  public MarketplaceAdminExecutionAuthorityStatus {
    if (requiresUserSignature) {
      throw new Web3InvalidInputException(
          "marketplace admin execution must not require user signature");
    }
    if (authorityModel == null || authorityModel.isBlank()) {
      throw new Web3InvalidInputException("authorityModel is required");
    }
    if (relayerRegistrationStatus == null || relayerRegistrationStatus.isBlank()) {
      throw new Web3InvalidInputException("relayerRegistrationStatus is required");
    }
    if (!isKnownRelayerRegistrationStatus(relayerRegistrationStatus)) {
      throw new Web3InvalidInputException("relayerRegistrationStatus is invalid");
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

  public static MarketplaceAdminExecutionAuthorityStatus serverRelayerOnly() {
    return new MarketplaceAdminExecutionAuthorityStatus(
        false, SERVER_RELAYER_ONLY, false, null, false, RELAYER_REGISTRATION_UNCHECKED);
  }

  private static boolean isKnownRelayerRegistrationStatus(String value) {
    return RELAYER_REGISTRATION_UNCHECKED.equals(value)
        || RELAYER_REGISTRATION_REGISTERED.equals(value)
        || RELAYER_REGISTRATION_NOT_REGISTERED.equals(value)
        || RELAYER_REGISTRATION_CHECK_FAILED.equals(value);
  }
}
