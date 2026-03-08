package momzzangseven.mztkbe.modules.verification.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;

/** Query use case for loading the latest verification snapshot for a user. */
public interface GetLatestVerificationResultUseCase {
  Optional<VerificationRequest> execute(Long userId);
}
