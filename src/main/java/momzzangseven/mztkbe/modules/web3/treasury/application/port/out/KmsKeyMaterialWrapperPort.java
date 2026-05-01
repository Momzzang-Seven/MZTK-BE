package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

/**
 * Pure-crypto out-port that wraps a 32-byte secp256k1 private key with the RSA-4096 public key
 * returned by {@code KmsKeyLifecyclePort.getParametersForImport()}.
 *
 * <p>Separated from {@link KmsKeyLifecyclePort} so that the wrapping algorithm (RSA-OAEP-SHA-256)
 * can be implemented locally with BouncyCastle / JCE rather than via AWS SDK, keeping the HSM-bound
 * side of the contract minimal.
 */
public interface KmsKeyMaterialWrapperPort {

  /**
   * Wrap the supplied raw private key with RSA-OAEP-SHA-256 using the supplied DER-encoded RSA-4096
   * public key.
   *
   * @param rawKey 32-byte secp256k1 private key in big-endian
   * @param wrappingPublicKey DER-encoded RSA-4096 public key from {@code
   *     KmsKeyLifecyclePort.ImportParams#wrappingPublicKey()}
   * @return ciphertext suitable for {@code KmsKeyLifecyclePort.importKeyMaterial}
   */
  byte[] wrap(byte[] rawKey, byte[] wrappingPublicKey);
}
