package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when an AWS KMS {@code DescribeKey} request cannot be completed.
 *
 * <p>Causes include KMS API throttling, transient 5xx errors, IAM permission failures (the caller
 * lacks {@code kms:DescribeKey} on the target key), or unexpected SDK exceptions. {@code
 * DescribeKey} is read-only and used by {@code VerifyTreasuryWalletForSignUseCase} to gate signing
 * — a failure here means the application cannot determine the key's lifecycle state and therefore
 * must not sign.
 *
 * <p>This is intentionally distinct from {@link KmsSignFailedException} so that IAM policies can
 * separate read (DescribeKey) and write (Sign) failure modes, and so that retry policies can treat
 * the two paths independently.
 */
public class KmsKeyDescribeFailedException extends BusinessException {

  /**
   * Create a new {@link KmsKeyDescribeFailedException} with a descriptive message.
   *
   * @param message human-readable description of the DescribeKey failure
   */
  public KmsKeyDescribeFailedException(String message) {
    super(ErrorCode.WEB3_KMS_KEY_DESCRIBE_FAILED, message);
  }

  /**
   * Create a new {@link KmsKeyDescribeFailedException} wrapping an underlying cause.
   *
   * @param message human-readable description of the DescribeKey failure
   * @param cause underlying exception that triggered the failure (typically an AWS SDK exception)
   */
  public KmsKeyDescribeFailedException(String message, Throwable cause) {
    super(ErrorCode.WEB3_KMS_KEY_DESCRIBE_FAILED, message, cause);
  }
}
