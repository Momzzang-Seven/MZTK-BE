package momzzangseven.mztkbe.modules.web3.transfer.domain.vo;

import java.math.BigDecimal;

public record TransferRuntimeConfig(
    long chainId,
    String tokenContractAddress,
    int retryBackoffSeconds,
    String delegationBatchImplAddress,
    String delegationDefaultReceiverAddress,
    String sponsorWalletAlias,
    String sponsorKeyEncryptionKeyB64,
    long sponsorMaxGasLimit,
    BigDecimal sponsorMaxTransferAmountEth,
    BigDecimal sponsorPerTxCapEth,
    BigDecimal sponsorPerDayUserCapEth,
    int authorizationTtlSeconds,
    String cleanupZone,
    int cleanupRetentionDays,
    int cleanupBatchSize) {

  public TransferRuntimeConfig {
    if (chainId <= 0) {
      throw new IllegalArgumentException("chainId must be positive");
    }
    if (isBlank(tokenContractAddress)) {
      throw new IllegalArgumentException("tokenContractAddress is required");
    }
    if (retryBackoffSeconds <= 0) {
      throw new IllegalArgumentException("retryBackoffSeconds must be positive");
    }
    if (isBlank(delegationBatchImplAddress) || isBlank(delegationDefaultReceiverAddress)) {
      throw new IllegalArgumentException("delegation addresses are required");
    }
    if (isBlank(sponsorWalletAlias) || isBlank(sponsorKeyEncryptionKeyB64)) {
      throw new IllegalArgumentException("sponsor key settings are required");
    }
    if (sponsorMaxGasLimit <= 0) {
      throw new IllegalArgumentException("sponsorMaxGasLimit must be positive");
    }
    if (isNullOrNegative(sponsorMaxTransferAmountEth)
        || isNullOrNegative(sponsorPerTxCapEth)
        || isNullOrNegative(sponsorPerDayUserCapEth)) {
      throw new IllegalArgumentException("sponsor caps must be >= 0");
    }
    if (authorizationTtlSeconds <= 0) {
      throw new IllegalArgumentException("authorizationTtlSeconds must be positive");
    }
    if (isBlank(cleanupZone) || cleanupRetentionDays <= 0 || cleanupBatchSize <= 0) {
      throw new IllegalArgumentException("cleanup config is invalid");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean isNullOrNegative(BigDecimal value) {
    return value == null || value.signum() < 0;
  }
}
