package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsKeyDescribePort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Non-production adapter for {@link KmsKeyDescribePort} that always reports {@link
 * KmsKeyState#ENABLED}.
 *
 * <p>The real KMS-backed describe adapter lives behind the prod-only {@code KmsClient} bean and is
 * delivered in MOM-340 Commit 1-4. For local / dev / test / integration / E2E profiles the
 * verification chain ({@code DescribeKmsKeyService} + treasury {@code
 * VerifyTreasuryWalletForSignService}) treats every key as available, which mirrors how the
 * non-prod {@code LocalEcSignerAdapter} stands in for the KMS sign call: domain logic is exercised
 * end-to-end without requiring real AWS resources.
 */
@Component
@Profile("!prod")
public class LocalKmsKeyDescribeAdapter implements KmsKeyDescribePort {

  @Override
  public KmsKeyState describe(String kmsKeyId) {
    return KmsKeyState.ENABLED;
  }
}
