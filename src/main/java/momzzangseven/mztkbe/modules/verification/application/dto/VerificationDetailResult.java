package momzzangseven.mztkbe.modules.verification.application.dto;

import java.time.LocalDate;
import lombok.Builder;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

@Builder
public record VerificationDetailResult(
    String verificationId,
    VerificationKind verificationKind,
    VerificationStatus verificationStatus,
    LocalDate exerciseDate,
    RejectionReasonCode rejectionReasonCode,
    String rejectionReasonDetail,
    FailureCode failureCode) {}
