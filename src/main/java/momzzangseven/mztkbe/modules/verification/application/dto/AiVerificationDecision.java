package momzzangseven.mztkbe.modules.verification.application.dto;

import java.time.LocalDate;
import lombok.Builder;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;

@Builder
public record AiVerificationDecision(
    boolean approved,
    LocalDate exerciseDate,
    RejectionReasonCode rejectionReasonCode,
    String rejectionReasonDetail) {}
