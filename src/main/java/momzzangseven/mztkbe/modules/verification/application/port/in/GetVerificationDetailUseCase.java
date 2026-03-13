package momzzangseven.mztkbe.modules.verification.application.port.in;

import momzzangseven.mztkbe.modules.verification.application.dto.VerificationDetailResult;

public interface GetVerificationDetailUseCase {
  VerificationDetailResult execute(Long userId, String verificationId);
}
