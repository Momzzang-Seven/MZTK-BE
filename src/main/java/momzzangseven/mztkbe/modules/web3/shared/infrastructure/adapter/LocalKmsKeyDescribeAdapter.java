package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsKeyDescribePort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Non-KMS adapter for {@link KmsKeyDescribePort} that always reports {@link KmsKeyState#ENABLED}.
 *
 * <p>Wired whenever {@code web3.kms.enabled} is false or unset (the inverse of {@link
 * KmsKeyDescribeAdapter}). For environments that have not opted into real AWS KMS the verification
 * chain ({@code DescribeKmsKeyService} + treasury {@code VerifyTreasuryWalletForSignService})
 * treats every key as available, which mirrors how {@code LocalEcSignerAdapter} stands in for the
 * KMS sign call: domain logic is exercised end-to-end without requiring real AWS resources.
 */
@Component
@ConditionalOnProperty(name = "web3.kms.enabled", havingValue = "false", matchIfMissing = true)
public class LocalKmsKeyDescribeAdapter implements KmsKeyDescribePort {

  @Override
  public KmsKeyState describe(String kmsKeyId) {
    return KmsKeyState.ENABLED;
  }
}
