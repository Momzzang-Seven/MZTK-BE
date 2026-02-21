package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigDecimal;

public interface LoadTransferRuntimeConfigPort {

  TransferRuntimeConfig load();

  record TransferRuntimeConfig(
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
      int cleanupBatchSize) {}
}
