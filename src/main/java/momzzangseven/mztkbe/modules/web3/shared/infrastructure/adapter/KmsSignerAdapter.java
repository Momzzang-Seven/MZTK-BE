package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsSignerPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.crypto.DerToVrsConverter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

/**
 * Production-only AWS KMS-backed implementation of {@link KmsSignerPort}.
 *
 * <p>Calls AWS KMS {@code Sign(MessageType=DIGEST, SigningAlgorithm=ECDSA_SHA_256)} against the
 * supplied {@code kmsKeyId}, then delegates the DER decode + EIP-2 low-s correction + recovery-id
 * determination to {@link DerToVrsConverter}. The 32-byte keccak digest never leaves the JVM in
 * plaintext form (only as the in-memory request payload to AWS) and the private key never leaves
 * the AWS HSM.
 *
 * <p>This adapter is gated on the {@code prod} Spring profile so that local / dev / test /
 * integration / E2E environments — which lack the prod-only {@code KmsClient} bean — instead
 * receive {@link LocalEcSignerAdapter}. Exactly one bean of {@link KmsSignerPort} is wired per
 * environment.
 *
 * <p><b>Logging hygiene</b> — Per design §8 we never log the digest, DER signature, or recovered
 * {@code (r, s, v)}. Only {@code kmsKeyId} (a non-secret identifier) and high-level outcome are
 * emitted, keeping CloudWatch / log aggregator content free of any byte material that could weaken
 * the HSM boundary.
 *
 * <p><b>Throttle / retry</b> — Per design §9 this adapter does not retry internally. Any {@link
 * KmsException} (throttling, 5xx, IAM denial, key state errors) is wrapped into {@link
 * KmsSignFailedException} and propagated; the upstream caller (e.g. {@code TransactionIssuerWorker}
 * via {@code Web3TxFailureReason.KMS_SIGN_FAILED}) decides whether to retry under its existing
 * exponential-backoff strategy.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class KmsSignerAdapter implements KmsSignerPort {

  private final KmsClient kmsClient;

  /**
   * Sign the supplied 32-byte digest using AWS KMS and return the canonical Ethereum {@code (r, s,
   * v)} form.
   *
   * @param kmsKeyId fully-qualified KMS key id or alias to sign with
   * @param digest 32-byte keccak256 digest of the unsigned transaction payload
   * @param expectedAddress Ethereum address that the recovered public key must match
   * @return canonical {@link Vrs} signature components produced by {@link DerToVrsConverter}
   * @throws KmsSignFailedException when the underlying AWS KMS {@code Sign} call fails
   */
  @Override
  public Vrs signDigest(String kmsKeyId, byte[] digest, String expectedAddress) {
    final SignRequest request =
        SignRequest.builder()
            .keyId(kmsKeyId)
            .messageType(MessageType.DIGEST)
            .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
            .message(SdkBytes.fromByteArray(digest))
            .build();

    final SignResponse response;
    try {
      response = kmsClient.sign(request);
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS Sign failed (kmsKeyId={}, awsErrorCode={})",
          kmsKeyId,
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw new KmsSignFailedException("KMS Sign API failed", ex);
    }

    return DerToVrsConverter.convert(response.signature().asByteArray(), digest, expectedAddress);
  }
}
