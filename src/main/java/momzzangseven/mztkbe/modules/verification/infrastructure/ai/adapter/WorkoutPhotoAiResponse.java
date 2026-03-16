package momzzangseven.mztkbe.modules.verification.infrastructure.ai.adapter;

import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;

record WorkoutPhotoAiResponse(
    boolean workoutPhoto, RejectionReasonCode rejectionReasonCode, double confidenceScore) {}
