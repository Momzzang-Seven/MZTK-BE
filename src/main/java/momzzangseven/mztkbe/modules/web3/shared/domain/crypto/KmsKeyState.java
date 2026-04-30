package momzzangseven.mztkbe.modules.web3.shared.domain.crypto;

/**
 * Application-side projection of an AWS KMS key's lifecycle state, narrowed to the values that
 * govern whether the key is currently usable for {@code Sign}.
 *
 * <p>The mapping from raw AWS {@code KeyState} strings is performed in the infrastructure adapter
 * (see {@code KmsKeyDescribeAdapter}); the application layer only consumes this enum so that
 * services such as {@code VerifyTreasuryWalletForSignService} can reason about signability without
 * importing the AWS SDK.
 */
public enum KmsKeyState {

  /** Key is enabled and may be used for cryptographic operations including {@code Sign}. */
  ENABLED,

  /**
   * Key has been administratively disabled. Cryptographic operations are rejected by KMS until the
   * key is re-enabled.
   */
  DISABLED,

  /**
   * Key is scheduled for deletion. Cryptographic operations are rejected and the key will be
   * permanently destroyed once the pending window elapses.
   */
  PENDING_DELETION,

  /**
   * Key was created without key material (external key origin) and is waiting for material to be
   * imported via {@code ImportKeyMaterial}. Not signable until import completes.
   */
  PENDING_IMPORT,

  /**
   * Catch-all for any KMS state that is not explicitly modelled above (for example {@code
   * CREATING}, {@code UPDATING}, {@code CANCELLED}, or future AWS additions). Treat as not
   * signable.
   */
  UNAVAILABLE
}
