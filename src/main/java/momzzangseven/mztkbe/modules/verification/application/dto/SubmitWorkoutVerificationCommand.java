package momzzangseven.mztkbe.modules.verification.application.dto;

import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;

public record SubmitWorkoutVerificationCommand(
    Long userId, String tmpObjectKey, VerificationKind kind) {}
