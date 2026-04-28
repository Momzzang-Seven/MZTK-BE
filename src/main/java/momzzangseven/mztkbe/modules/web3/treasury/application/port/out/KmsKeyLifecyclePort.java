package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

/**
 * Out-port for KMS key lifecycle operations driven by treasury-side use cases (provision, disable,
 * archive). Wraps the AWS KMS control-plane API into a treasury-shaped interface so that the
 * application layer does not depend on the AWS SDK.
 *
 * <p>The provisioning flow consumes {@link #createKey()}, {@link #getParametersForImport(String)},
 * {@link #importKeyMaterial(String, byte[], byte[])}, and {@link #createAlias(String, String)} in
 * sequence. The retirement flow consumes {@link #disableKey(String)} (on {@code disable}) and
 * {@link #scheduleKeyDeletion(String, int)} (on {@code archive}).
 */
public interface KmsKeyLifecyclePort {

  /**
   * Create a new {@code ECC_SECG_P256K1} / {@code SIGN_VERIFY} key with {@code Origin=EXTERNAL} so
   * that subsequent {@link #importKeyMaterial(String, byte[], byte[])} can install the supplied
   * private key material.
   *
   * @return KMS key id of the freshly created (but still un-provisioned) key
   */
  String createKey();

  /**
   * Fetch a wrapping public key + import token bound to the supplied {@code kmsKeyId}.
   * Implementations request {@code WrappingAlgorithm=RSAES_OAEP_SHA_256} and {@code
   * WrappingKeySpec=RSA_4096} so the caller can RSA-OAEP-SHA-256 wrap the raw secp256k1 private key
   * locally before uploading.
   */
  ImportParams getParametersForImport(String kmsKeyId);

  /**
   * Upload pre-wrapped key material into the supplied {@code kmsKeyId} using the matching {@code
   * importToken}. Implementations issue {@code ExpirationModel=KEY_MATERIAL_DOES_NOT_EXPIRE} so the
   * material persists across rotations of the wrapping public key.
   */
  void importKeyMaterial(String kmsKeyId, byte[] encryptedKeyMaterial, byte[] importToken);

  /**
   * Bind the supplied {@code alias} ({@code TreasuryRole#toAlias()}) to {@code kmsKeyId}.
   * Implementations may require the alias to be globally unique within the AWS account.
   */
  void createAlias(String alias, String kmsKeyId);

  /** Disable the supplied key without scheduling deletion. Used by {@code disable()} flows. */
  void disableKey(String kmsKeyId);

  /**
   * Schedule the supplied key for permanent deletion with the given pending window (in days). Used
   * by {@code archive()} flows.
   */
  void scheduleKeyDeletion(String kmsKeyId, int pendingWindowDays);

  /**
   * Wrapping public key + import token bundle returned by {@link #getParametersForImport(String)}.
   * Both arrays are owned by the caller after the call returns; implementations are not required to
   * defensively copy.
   *
   * @param wrappingPublicKey DER-encoded RSA-4096 public key used by the caller for
   *     RSA-OAEP-SHA-256 wrapping
   * @param importToken opaque token that must be passed back unchanged to {@link
   *     #importKeyMaterial(String, byte[], byte[])}
   */
  record ImportParams(byte[] wrappingPublicKey, byte[] importToken) {}
}
