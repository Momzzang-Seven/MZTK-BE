package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/**
 * Capability handle (DTO) for a KMS-backed treasury signer; carries no secret material.
 *
 * <p>Bundles the three identifiers a caller needs to invoke the {@code SignDigestPort}: the
 * canonical wallet alias (operational lookup key), the AWS KMS key id (signing target), and the
 * derived EVM address (recovery-id determinant). Replaces the historical {@code
 * treasuryPrivateKeyHex} field on signer commands so plaintext key bytes never cross module
 * boundaries.
 *
 * <p>Used by {@code web3/transaction} for reward EIP-1559 signing and {@code web3/eip7702} for
 * sponsor EIP-7702 signing.
 *
 * @param walletAlias canonical alias bound to the treasury wallet (e.g. {@code reward-treasury})
 * @param kmsKeyId AWS KMS key identifier (alias ARN or key id)
 * @param walletAddress 0x-prefixed EVM address derived from the KMS-held secp256k1 public key
 */
public record TreasurySigner(String walletAlias, String kmsKeyId, String walletAddress) {

  /** Compact constructor — enforces non-blank alias / key id and validates the EVM address. */
  public TreasurySigner {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias required");
    }
    if (kmsKeyId == null || kmsKeyId.isBlank()) {
      throw new Web3InvalidInputException("kmsKeyId required");
    }
    EvmAddress.of(walletAddress);
  }
}
