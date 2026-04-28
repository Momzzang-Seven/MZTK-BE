package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;

/**
 * Treasury-owned out-port for ECDSA digest signing through KMS, used by the provisioning sanity
 * round-trip immediately after {@code ImportKeyMaterial}.
 *
 * <p>Implemented by a bridging adapter that delegates to the {@code shared} module's {@code
 * SignDigestUseCase}; treasury services do not depend on shared types directly so this port stays
 * in the treasury package boundary.
 */
public interface SignDigestPort {

  /**
   * Sign the supplied 32-byte digest with the supplied KMS key id and verify the recovered address
   * matches {@code expectedAddress}.
   *
   * @param kmsKeyId KMS key id to invoke {@code Sign} against
   * @param digest 32-byte EIP-1559 / EIP-7702 (or test) digest
   * @param expectedAddress {@code 0x}-prefixed Ethereum address that the recovered public key must
   *     match
   * @return canonical {@code (r, s, v)} signature
   */
  Vrs signDigest(String kmsKeyId, byte[] digest, String expectedAddress);
}
