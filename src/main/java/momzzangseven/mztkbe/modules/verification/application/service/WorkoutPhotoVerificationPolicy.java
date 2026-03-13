package momzzangseven.mztkbe.modules.verification.application.service;

import java.nio.file.Path;
import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.springframework.stereotype.Component;

@Component
public class WorkoutPhotoVerificationPolicy implements VerificationSubmissionPolicy {

  @Override
  public VerificationKind kind() {
    return VerificationKind.WORKOUT_PHOTO;
  }

  @Override
  public boolean allowsExtension(String extension) {
    return extension.equals("jpg")
        || extension.equals("jpeg")
        || extension.equals("heic")
        || extension.equals("heif");
  }

  @Override
  public boolean requiresExifValidation() {
    return true;
  }

  @Override
  public String sourceRefPrefix() {
    return "workout-photo-verification:";
  }

  @Override
  public int analysisMaxLongEdge() {
    return 1024;
  }

  @Override
  public double analysisWebpQuality() {
    return 0.80d;
  }

  @Override
  public AiVerificationDecision analyze(
      WorkoutImageAiPort workoutImageAiPort, Path analysisImagePath) {
    return workoutImageAiPort.analyzeWorkoutPhoto(analysisImagePath);
  }

  @Override
  public LocalDate resolveVerifiedExerciseDate(AiVerificationDecision decision) {
    return null;
  }
}
