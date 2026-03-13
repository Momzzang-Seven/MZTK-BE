package momzzangseven.mztkbe.modules.verification.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitWorkoutRecordVerificationRequestDTO(@NotBlank String tmpObjectKey) {}
