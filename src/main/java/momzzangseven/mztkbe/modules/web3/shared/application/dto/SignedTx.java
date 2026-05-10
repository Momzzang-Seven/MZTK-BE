package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Signed EIP-1559 transaction envelope returned by the codec / sign UseCase pipeline.
 *
 * @param rawTx 0x-prefixed hex of the type-2 signed transaction bytes
 * @param txHash keccak256 of {@link #rawTx}, 0x-prefixed hex
 */
public record SignedTx(String rawTx, String txHash) {

  /** Compact constructor — both fields must be non-blank. */
  public SignedTx {
    if (rawTx == null || rawTx.isBlank()) {
      throw new Web3InvalidInputException("rawTx is required");
    }
    if (txHash == null || txHash.isBlank()) {
      throw new Web3InvalidInputException("txHash is required");
    }
  }
}
