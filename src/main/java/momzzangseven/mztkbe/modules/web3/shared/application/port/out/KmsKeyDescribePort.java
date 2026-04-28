package momzzangseven.mztkbe.modules.web3.shared.application.port.out;

import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;

/**
 * Output port for the read-only KMS {@code DescribeKey} call.
 *
 * <p>Intentionally separated from any lifecycle-mutation port (see design doc §3 ISP rationale:
 * {@code KmsKeyLifecyclePort}) so the IAM role attached to the runtime signer can be granted {@code
 * kms:DescribeKey} without {@code kms:DisableKey} / {@code kms:ScheduleKeyDeletion}.
 */
public interface KmsKeyDescribePort {

  /**
   * Look up the lifecycle state of the supplied KMS key id.
   *
   * @param kmsKeyId fully-qualified KMS key id (or alias) to describe
   * @return {@link KmsKeyState} representing the key's current signability
   * @throws KmsKeyDescribeFailedException when the KMS API call itself fails (throttling, 5xx,
   *     permission denial, or unexpected SDK error)
   */
  KmsKeyState describe(String kmsKeyId);
}
