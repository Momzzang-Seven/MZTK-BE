package momzzangseven.mztkbe.modules.verification.application.port.in;

import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;

public interface SubmitWorkoutRecordVerificationUseCase {
  SubmitWorkoutVerificationResult execute(SubmitWorkoutVerificationCommand command);
}
