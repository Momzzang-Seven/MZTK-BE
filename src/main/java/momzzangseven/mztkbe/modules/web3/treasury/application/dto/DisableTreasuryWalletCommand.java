package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Input for {@code DisableTreasuryWalletUseCase}. Carries the alias of the wallet to disable plus
 * the operator id required for the audit trail.
 *
 * @param walletAlias canonical alias of the wallet to disable
 * @param operatorUserId admin user id invoking the operation
 */
public record DisableTreasuryWalletCommand(String walletAlias, Long operatorUserId) {

  public DisableTreasuryWalletCommand {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias is required");
    }
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new Web3InvalidInputException("operatorUserId must be positive");
    }
  }
}
