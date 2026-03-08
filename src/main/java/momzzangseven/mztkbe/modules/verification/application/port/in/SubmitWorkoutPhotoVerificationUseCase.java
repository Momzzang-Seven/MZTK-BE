package momzzangseven.mztkbe.modules.verification.application.port.in;

import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;

/** Command use case for submitting a workout photo verification request. */
public interface SubmitWorkoutPhotoVerificationUseCase {
  VerificationRequest submit(Long userId, String contentType, long sizeBytes, byte[] imageBytes);
}
