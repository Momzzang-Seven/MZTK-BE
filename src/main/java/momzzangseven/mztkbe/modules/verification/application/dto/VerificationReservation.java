package momzzangseven.mztkbe.modules.verification.application.dto;

import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;

public record VerificationReservation(
    VerificationRequest request, WorkoutUploadReference upload, boolean readyForAnalysis) {

  public static VerificationReservation existing(VerificationRequest request) {
    return new VerificationReservation(request, null, false);
  }

  public static VerificationReservation analyzing(
      VerificationRequest request, WorkoutUploadReference upload) {
    return new VerificationReservation(request, upload, true);
  }
}
