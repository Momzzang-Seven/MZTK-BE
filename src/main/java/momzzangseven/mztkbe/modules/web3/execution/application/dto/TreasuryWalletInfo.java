package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Execution-local projection of a treasury wallet snapshot.
 *
 * <p>Carries only the fields the execution sponsor signing flow (EIP-7702 plus the internal-batch
 * EIP-1559 sponsor in 3-5b) needs, decoupling execution callers from the {@code web3/treasury}
 * aggregate. Mirrors the transaction-side {@code TreasuryWalletInfo}; both are intentionally
 * separate so each module owns its own bridging DTO.
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
