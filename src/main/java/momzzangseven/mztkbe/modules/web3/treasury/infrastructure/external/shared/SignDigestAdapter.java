package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.external.shared;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter that fulfils the treasury-local {@link SignDigestPort} by delegating to the
 * {@code shared} module's {@link SignDigestUseCase}. Used by {@code ProvisionTreasuryKeyService}
 * for the post-import sanity round-trip.
 */
@Component
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
