package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.SubmitWorkoutPhotoVerificationUseCase;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubmitWorkoutPhotoVerificationService
    implements SubmitWorkoutPhotoVerificationUseCase {

  private final VerificationSubmissionOrchestrator verificationSubmissionOrchestrator;
  private final WorkoutPhotoVerificationPolicy workoutPhotoVerificationPolicy;

  @Override
  public SubmitWorkoutVerificationResult execute(SubmitWorkoutVerificationCommand command) {
    return verificationSubmissionOrchestrator.submit(command, workoutPhotoVerificationPolicy);
  }
}
