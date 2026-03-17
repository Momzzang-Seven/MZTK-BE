package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.nio.file.Path;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;

public interface WorkoutImageAiPort {
  AiVerificationDecision analyzeWorkoutPhoto(Path analysisImagePath);

  AiVerificationDecision analyzeWorkoutRecord(Path analysisImagePath);
}
