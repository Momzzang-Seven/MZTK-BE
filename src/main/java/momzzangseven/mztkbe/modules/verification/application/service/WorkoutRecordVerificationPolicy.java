package momzzangseven.mztkbe.modules.verification.application.service;

import java.nio.file.Path;
import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.springframework.stereotype.Component;

@Component
public class WorkoutRecordVerificationPolicy implements VerificationSubmissionPolicy {

  @Override
  public VerificationKind kind() {
    return VerificationKind.WORKOUT_RECORD;
  }

  @Override
  public boolean allowsExtension(String extension) {
    return extension.equals("jpg")
        || extension.equals("jpeg")
        || extension.equals("png")
        || extension.equals("heic")
        || extension.equals("heif");
  }

  @Override
  public boolean requiresExifValidation() {
    return false;
  }

  @Override
  public String sourceRefPrefix() {
    return "workout-record-verification:";
  }

  @Override
  public int analysisMaxLongEdge() {
    return 1536;
  }

  @Override
  public double analysisWebpQuality() {
    return 0.85d;
  }

  @Override
  public AiVerificationDecision analyze(
      WorkoutImageAiPort workoutImageAiPort, Path analysisImagePath) {
    return workoutImageAiPort.analyzeWorkoutRecord(analysisImagePath);
  }

  @Override
  public LocalDate resolveVerifiedExerciseDate(AiVerificationDecision decision) {
    return decision.exerciseDate();
  }
}
