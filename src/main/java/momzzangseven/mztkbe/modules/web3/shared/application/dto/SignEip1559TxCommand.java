package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Command for {@link
 * momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignEip1559TxUseCase#sign}.
 *
 * <p>Captures the entire input set required to build, digest, sign, and assemble an EIP-1559
 * transaction in one round-trip — keeping the cross-module signature stable as the underlying
 * codec / signing layer evolves.
 *
 * @param fields validated EIP-1559 field set
 * @param kmsKeyId AWS KMS key identifier (alias ARN or key id) bound to the signer
 * @param expectedSignerAddress 0x-prefixed 20-byte EVM address whose public key the signature must
 *     recover to (used by the shared signing layer to determine the correct recovery id)
 */
public record SignEip1559TxCommand(
    Eip1559Fields fields, String kmsKeyId, String expectedSignerAddress) {

  /** Compact constructor — verifies non-null / non-blank surface invariants. */
  public SignEip1559TxCommand {
    if (fields == null) {
      throw new Web3InvalidInputException("fields is required");
    }
    if (kmsKeyId == null || kmsKeyId.isBlank()) {
      throw new Web3InvalidInputException("kmsKeyId is required");
    }
    if (expectedSignerAddress == null || expectedSignerAddress.isBlank()) {
      throw new Web3InvalidInputException("expectedSignerAddress is required");
    }
  }
}
