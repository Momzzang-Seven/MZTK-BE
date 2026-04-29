package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;

/**
 * Out-port for KMS key lifecycle operations driven by treasury-side use cases (provision, disable,
 * archive). Wraps the AWS KMS control-plane API into a treasury-shaped interface so that the
 * application layer does not depend on the AWS SDK.
 *
 * <p>The provisioning flow consumes {@link #createKey()}, {@link #getParametersForImport(String)},
 * {@link #importKeyMaterial(String, byte[], byte[])}, and {@link #createAlias(String, String)} in
 * sequence. The retirement flow consumes {@link #disableKey(String)} (on {@code disable}) and
 * {@link #scheduleKeyDeletion(String, int)} (on {@code archive}). {@link #updateAlias(String,
 * String)} and {@link #describeAliasTarget(String)} support idempotent recovery when {@code
 * CreateAlias} encounters a stale alias from a prior failed provision run.
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
   *
   * <p>Implementations must translate AWS {@code AlreadyExistsException} into {@link
   * momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException} so the provisioning
   * service can drive idempotent recovery via {@link #describeAliasTarget(String)} + {@link
   * #updateAlias(String, String)} without depending on the AWS SDK.
   */
  void createAlias(String alias, String kmsKeyId);

  /**
   * Re-target the supplied {@code alias} to {@code newKmsKeyId}. Used by the provisioning service
   * to recover a stale alias that a prior failed run left bound to a {@code PENDING_DELETION} /
   * {@code DISABLED} key.
   */
  void updateAlias(String alias, String newKmsKeyId);

  /**
   * Inspect the {@link KmsKeyState} of the key currently bound to {@code alias}. Returns {@link
   * KmsKeyState#UNAVAILABLE} when the alias does not exist; the caller treats {@link
   * KmsKeyState#PENDING_DELETION} / {@link KmsKeyState#DISABLED} as recoverable ghosts and
   * everything else as a hard conflict.
   */
  KmsKeyState describeAliasTarget(String alias);

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
