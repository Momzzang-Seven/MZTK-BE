package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.verification.VerificationNotFoundException;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationDetailResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetVerificationDetailUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationQueryService implements GetVerificationDetailUseCase {

  private final VerificationRequestPort verificationRequestPort;

  @Override
  public VerificationDetailResult execute(Long userId, String verificationId) {
    VerificationRequest request =
        verificationRequestPort
            .findByVerificationIdAndUserId(verificationId, userId)
            .orElseThrow(VerificationNotFoundException::new);
    return VerificationDetailResult.builder()
        .verificationId(request.getVerificationId())
        .verificationKind(request.getVerificationKind())
        .verificationStatus(request.getStatus())
        .exerciseDate(request.getExerciseDate())
        .rejectionReasonCode(request.getRejectionReasonCode())
        .rejectionReasonDetail(request.getRejectionReasonDetail())
        .failureCode(request.getFailureCode())
        .build();
  }
}
