package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.external.shared;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.DescribeKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter that fulfils the treasury-local {@link DescribeKmsKeyPort} by delegating to the
 * {@code shared} module's {@link DescribeKmsKeyUseCase}.
 *
 * <p>Keeps the dependency direction inward — treasury services only see the treasury-local port,
 * while this adapter is the single coupling point with the shared module.
 */
@Component
@RequiredArgsConstructor
public class DescribeKmsKeyAdapter implements DescribeKmsKeyPort {

  private final DescribeKmsKeyUseCase describeKmsKeyUseCase;

  @Override
  public KmsKeyState describe(String kmsKeyId) {
    return describeKmsKeyUseCase.execute(kmsKeyId);
  }
}
