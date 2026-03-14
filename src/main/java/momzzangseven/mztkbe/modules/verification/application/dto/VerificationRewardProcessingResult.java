package momzzangseven.mztkbe.modules.verification.application.dto;

import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;

public record VerificationRewardProcessingResult(
    VerificationRequest request, int grantedXp, String completedMethodSourceRef) {}
