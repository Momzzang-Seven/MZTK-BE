package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Input for {@code ArchiveTreasuryWalletUseCase}. Archive transitions a {@code DISABLED} wallet to
 * {@code ARCHIVED} and schedules the backing KMS key for deletion.
 *
 * @param walletAlias canonical alias of the wallet to archive
 * @param operatorUserId admin user id invoking the operation
 */
public record ArchiveTreasuryWalletCommand(String walletAlias, Long operatorUserId) {

  public ArchiveTreasuryWalletCommand {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias is required");
    }
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new Web3InvalidInputException("operatorUserId must be positive");
    }
  }
}
