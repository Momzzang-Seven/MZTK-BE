package momzzangseven.mztkbe.modules.verification.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitWorkoutVerificationRequestDTO(@NotBlank String tmpObjectKey) {}
