package momzzangseven.mztkbe.modules.web3.eip7702.application.port.out;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;

/** Eip7702-module own out-port for digest-based ECDSA signing via a KMS-backed signer. */
public interface SignDigestPort {

  /**
   * Sign the supplied 32-byte keccak256 digest with the KMS key bound to the given alias / address.
   *
   * @param kmsKeyId AWS KMS key identifier (alias ARN or key id)
   * @param digest 32-byte keccak256 digest of the unsigned typed-transaction envelope
   * @param expectedAddress 0x-prefixed 20-byte EVM address whose public key must recover from the
   *     signature; used by the shared layer to determine the correct recovery id
   * @return canonical {@code (r, s, v)} signature components
   */
  Vrs signDigest(String kmsKeyId, byte[] digest, String expectedAddress);
}
