package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsKeyDescribePort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsException;

/**
 * Production-only AWS KMS-backed implementation of {@link KmsKeyDescribePort}.
 *
 * <p>Performs a read-only {@code DescribeKey} call against AWS KMS and projects the raw {@link
 * KeyState} onto the application-side {@link KmsKeyState} enum. The 60-second {@code
 * DescribeKmsKeyService} cache absorbs the per-sign latency, so each {@code
 * TransactionIssuerWorker} batch makes at most one call to this adapter per kmsKeyId per minute.
 *
 * <p>This adapter is gated on the {@code web3.kms.enabled=true} property so that environments which
 * have not opted into real AWS KMS — and therefore lack the {@code KmsClient} bean produced by
 * {@code AwsKmsConfig} — instead receive {@link LocalKmsKeyDescribeAdapter}, which always reports
 * {@link KmsKeyState#ENABLED}.
 *
 * <p><b>Logging hygiene</b> — Per design §8 we never log key material; only {@code kmsKeyId} (a
 * non-secret identifier) and the AWS error code are emitted on failure.
 */
@Component
@ConditionalOnProperty(name = "web3.kms.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class KmsKeyDescribeAdapter implements KmsKeyDescribePort {

  private final KmsClient kmsClient;

  /**
   * Look up the lifecycle state of the supplied KMS key id via {@code DescribeKey}.
   *
   * @param kmsKeyId fully-qualified KMS key id or alias to describe
   * @return application-side {@link KmsKeyState} mapped from the AWS {@link KeyState}; {@link
   *     KmsKeyState#UNAVAILABLE} for any state we do not explicitly model (e.g. {@code CREATING},
   *     {@code UPDATING})
   * @throws KmsKeyDescribeFailedException when the underlying AWS KMS {@code DescribeKey} call
   *     fails
   */
  @Override
  public KmsKeyState describe(String kmsKeyId) {
    final DescribeKeyRequest request = DescribeKeyRequest.builder().keyId(kmsKeyId).build();

    final DescribeKeyResponse response;
    try {
      response = kmsClient.describeKey(request);
    } catch (SdkException ex) {
      log.warn(
          "AWS KMS DescribeKey failed (kmsKeyId={}, awsErrorCode={}, exception={})",
          kmsKeyId,
          awsErrorCodeOrNa(ex),
          ex.getClass().getSimpleName());
      throw new KmsKeyDescribeFailedException("KMS DescribeKey failed", ex);
    }

    return mapKeyState(response.keyMetadata().keyState());
  }

  /**
   * Map the raw AWS {@link KeyState} to the application-side {@link KmsKeyState}.
   *
   * <p>Anything not explicitly listed (CREATING, UPDATING, CANCELLED, future AWS additions) falls
   * through to {@link KmsKeyState#UNAVAILABLE} so that downstream signability checks fail closed.
   *
   * @param state the AWS-side key state, never {@code null} in practice for a successful describe
   * @return the application-side projection of the key state
   */
  private static KmsKeyState mapKeyState(KeyState state) {
    if (state == null) {
      return KmsKeyState.UNAVAILABLE;
    }
    return switch (state) {
      case ENABLED -> KmsKeyState.ENABLED;
      case DISABLED -> KmsKeyState.DISABLED;
      case PENDING_DELETION -> KmsKeyState.PENDING_DELETION;
      case PENDING_IMPORT -> KmsKeyState.PENDING_IMPORT;
      default -> KmsKeyState.UNAVAILABLE;
    };
  }

  private static String awsErrorCodeOrNa(SdkException ex) {
    if (ex instanceof KmsException kex && kex.awsErrorDetails() != null) {
      return kex.awsErrorDetails().errorCode();
    }
    return "n/a";
  }
}
