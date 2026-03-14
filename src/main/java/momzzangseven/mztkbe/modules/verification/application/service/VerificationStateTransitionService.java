package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationEvaluationResult;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationStateTransitionService {

  private final VerificationRequestPort verificationRequestPort;

  @Transactional
  public VerificationRequest applyEvaluation(
      String verificationId, VerificationEvaluationResult evaluation) {
    VerificationRequest locked =
        verificationRequestPort
            .findByVerificationIdForUpdate(verificationId)
            .orElseThrow(
                () -> new IllegalStateException("verification row must exist before completion"));

    if (locked.getStatus() == VerificationStatus.VERIFIED
        || locked.getStatus() == VerificationStatus.REJECTED) {
      return locked;
    }

    if (evaluation.failed()) {
      return verificationRequestPort.save(locked.fail(evaluation.failureCode()));
    }

    if (evaluation.rejected()) {
      return verificationRequestPort.save(applyRejection(locked, evaluation));
    }

    return verificationRequestPort.save(locked.verify(evaluation.exerciseDate(), evaluation.shotAtKst()));
  }

  private VerificationRequest applyRejection(
      VerificationRequest request, VerificationEvaluationResult evaluation) {
    if (evaluation.rejectionReasonCode() == RejectionReasonCode.MISSING_EXIF_METADATA) {
      return request.rejectForMissingExif();
    }
    if (evaluation.rejectionReasonCode() == RejectionReasonCode.EXIF_DATE_MISMATCH) {
      return request.rejectForExifDateMismatch(evaluation.exerciseDate(), evaluation.shotAtKst());
    }
    return request.reject(
        evaluation.rejectionReasonCode(),
        evaluation.rejectionReasonDetail(),
        evaluation.exerciseDate(),
        evaluation.shotAtKst());
  }
}
