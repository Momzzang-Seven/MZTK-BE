package momzzangseven.mztkbe.modules.verification.application.service;

import java.nio.file.Path;
import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;

public interface VerificationSubmissionPolicy {

  VerificationKind kind();

  boolean allowsExtension(String extension);

  boolean requiresExifValidation();

  String sourceRefPrefix();

  int analysisMaxLongEdge();

  double analysisWebpQuality();

  AiVerificationDecision analyze(WorkoutImageAiPort workoutImageAiPort, Path analysisImagePath);

  LocalDate resolveVerifiedExerciseDate(AiVerificationDecision decision);
}
