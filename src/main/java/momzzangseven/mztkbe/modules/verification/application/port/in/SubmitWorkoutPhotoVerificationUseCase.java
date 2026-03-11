package momzzangseven.mztkbe.modules.verification.application.port.in;

import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;

/** Command use case for synchronously submitting a workout photo verification request. */
public interface SubmitWorkoutPhotoVerificationUseCase {
  SubmitWorkoutVerificationResult submit(SubmitWorkoutVerificationCommand command);
}
