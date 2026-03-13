package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.SubmitWorkoutRecordVerificationUseCase;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubmitWorkoutRecordVerificationService
    implements SubmitWorkoutRecordVerificationUseCase {

  private final VerificationSubmissionOrchestrator verificationSubmissionOrchestrator;
  private final WorkoutRecordVerificationPolicy workoutRecordVerificationPolicy;

  @Override
  public SubmitWorkoutVerificationResult execute(SubmitWorkoutVerificationCommand command) {
    return verificationSubmissionOrchestrator.submit(command, workoutRecordVerificationPolicy);
  }
}
