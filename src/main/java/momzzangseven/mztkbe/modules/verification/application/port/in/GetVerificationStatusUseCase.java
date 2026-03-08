package momzzangseven.mztkbe.modules.verification.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;

/** Query use case for loading a single verification by public identifier. */
public interface GetVerificationStatusUseCase {
  Optional<VerificationRequest> execute(Long userId, String verificationId);
}
