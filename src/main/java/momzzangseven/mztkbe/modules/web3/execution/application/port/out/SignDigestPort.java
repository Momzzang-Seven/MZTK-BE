package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;

/**
 * Execution-module own out-port for digest-based ECDSA signing.
 *
 * <p>Per ARCHITECTURE.md, modules never depend on another module's in-port directly. The bridging
 * adapter {@code web3/execution/infrastructure/external/shared/SignDigestAdapter} translates this
 * port to the shared {@code SignDigestUseCase}, so the application layer of {@code web3/execution}
 * remains free of cross-module imports.
 */
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
