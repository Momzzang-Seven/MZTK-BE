package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.external.shared;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter that fulfils the eip7702-local {@link SignDigestPort} by delegating to the
 * {@code shared} module's {@link SignDigestUseCase}. Keeps the eip7702 application layer free of
 * cross-module imports per ARCHITECTURE.md.
 */
@Component("eip7702SignDigestAdapter")
@RequiredArgsConstructor
public class SignDigestAdapter implements SignDigestPort {

  private final SignDigestUseCase signDigestUseCase;

  @Override
  public Vrs signDigest(String kmsKeyId, byte[] digest, String expectedAddress) {
    SignDigestResult result =
        signDigestUseCase.execute(new SignDigestCommand(kmsKeyId, digest, expectedAddress));
    return new Vrs(result.r(), result.s(), result.v());
  }
}
