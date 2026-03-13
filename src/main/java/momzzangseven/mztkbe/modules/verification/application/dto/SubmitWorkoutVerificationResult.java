package momzzangseven.mztkbe.modules.verification.application.dto;

import java.time.LocalDate;
import lombok.Builder;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletionStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

@Builder
public record SubmitWorkoutVerificationResult(
    String verificationId,
    VerificationKind verificationKind,
    VerificationStatus verificationStatus,
    LocalDate exerciseDate,
    CompletionStatus completionStatus,
    int grantedXp,
    CompletedMethod completedMethod,
    RejectionReasonCode rejectionReasonCode,
    String rejectionReasonDetail,
    FailureCode failureCode) {}
