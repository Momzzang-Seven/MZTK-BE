package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.verification.VerificationNotFoundException;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationDetailResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetVerificationDetailUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationQueryService implements GetVerificationDetailUseCase {

  private final VerificationRequestPort verificationRequestPort;

  @Override
  public VerificationDetailResult execute(Long userId, String verificationId) {
    return verificationRequestPort
        .findByVerificationIdAndUserId(verificationId, userId)
        .map(VerificationDetailResult::from)
        .orElseThrow(VerificationNotFoundException::new);
  }
}
