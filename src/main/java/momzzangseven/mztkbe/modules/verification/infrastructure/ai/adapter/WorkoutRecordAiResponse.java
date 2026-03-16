package momzzangseven.mztkbe.modules.verification.infrastructure.ai.adapter;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;

record WorkoutRecordAiResponse(
    boolean workoutRecord,
    RejectionReasonCode rejectionReasonCode,
    boolean dateVisible,
    LocalDate exerciseDate,
    double confidenceScore) {}
