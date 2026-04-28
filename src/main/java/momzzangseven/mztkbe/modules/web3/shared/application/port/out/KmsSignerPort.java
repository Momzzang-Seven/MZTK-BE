package momzzangseven.mztkbe.modules.web3.shared.application.port.out;

import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;

/**
 * Output port for the actual KMS-backed digest signing operation.
 *
 * <p>The implementation calls AWS KMS {@code Sign(MessageType=DIGEST, ECDSA_SHA_256)}, decodes the
 * returned DER signature into {@code (r, s)}, applies EIP-2 low-s correction, and determines the
 * recovery id by testing both candidate {@code v} values against {@code expectedAddress}. The
 * resulting {@link Vrs} is canonical (low-s, content-immutable).
 */
public interface KmsSignerPort {

  /**
   * Sign the supplied 32-byte digest using the specified KMS key.
   *
   * @param kmsKeyId fully-qualified KMS key id (or alias) to sign with
   * @param digest 32-byte keccak256 digest of the unsigned transaction payload
   * @param expectedAddress Ethereum address that the recovered public key must match
   * @return canonical {@link Vrs} signature components
   * @throws KmsSignFailedException when the KMS API call itself fails (throttling, 5xx, permission
   *     denial, or unexpected SDK error)
   * @throws SignatureRecoveryException when the DER signature cannot be decoded or no recovery id
   *     yields {@code expectedAddress}
   */
  Vrs signDigest(String kmsKeyId, byte[] digest, String expectedAddress);
}
