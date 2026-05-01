package momzzangseven.mztkbe.modules.web3.shared.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsSignerPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.springframework.stereotype.Service;

/**
 * Default {@link SignDigestUseCase} implementation: pure orchestration over {@link KmsSignerPort}.
 *
 * <p>No caching, no transaction boundary — this service is deliberately stateless. The KMS adapter
 * is responsible for the actual signing, low-s correction, and recovery-id determination; this
 * layer only validates inputs and packages the result for cross-module consumers.
 */
@Service
@RequiredArgsConstructor
public class SignDigestService implements SignDigestUseCase {

  private final KmsSignerPort kmsSignerPort;

  @Override
  public SignDigestResult execute(SignDigestCommand command) {
    command.validate();
    Vrs vrs =
        kmsSignerPort.signDigest(command.kmsKeyId(), command.digest(), command.expectedAddress());
    return SignDigestResult.from(vrs);
  }
}
