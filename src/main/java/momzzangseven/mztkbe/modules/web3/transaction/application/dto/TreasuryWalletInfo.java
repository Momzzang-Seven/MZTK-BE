package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Transaction-local projection of a treasury wallet snapshot.
 *
 * <p>Carries only the fields the transaction issuer worker needs to drive an EIP-1559 reward
 * signing flow, decoupling the worker from the {@code web3/treasury} module's aggregate /
 * cross-module {@code TreasuryWalletView}. Construction happens inside a transaction-side bridging
 * adapter that translates the treasury input port output into this DTO.
 *
 * @param walletAlias canonical alias bound to the treasury wallet (always non-blank)
 * @param kmsKeyId AWS KMS key id backing the wallet (may be {@code null}/blank for legacy rows
 *     awaiting backfill)
 * @param walletAddress {@code 0x}-prefixed EVM address (may be {@code null}/blank for legacy rows)
 * @param active {@code true} only when the source wallet is in the {@code ACTIVE} lifecycle state
 */
public record TreasuryWalletInfo(
    String walletAlias, String kmsKeyId, String walletAddress, boolean active) {

  /** Compact constructor — the alias is the only universally required identifier. */
  public TreasuryWalletInfo {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias required");
    }
  }
}
