package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkoutPhotoVerificationPolicyTest {

  @Mock private WorkoutImageAiPort workoutImageAiPort;

  private final WorkoutPhotoVerificationPolicy policy = new WorkoutPhotoVerificationPolicy();

  @Test
  void allowsExpectedExtensionsAndRejectsOthers() {
    assertThat(policy.allowsExtension("jpg")).isTrue();
    assertThat(policy.allowsExtension("jpeg")).isTrue();
    assertThat(policy.allowsExtension("heic")).isTrue();
    assertThat(policy.allowsExtension("heif")).isTrue();
    assertThat(policy.allowsExtension("png")).isFalse();
  }

  @Test
  void exposesPhotoPolicySpec() {
    assertThat(policy.kind()).isEqualTo(VerificationKind.WORKOUT_PHOTO);
    assertThat(policy.requiresExifValidation()).isTrue();
    assertThat(policy.sourceRefPrefix()).isEqualTo("workout-photo-verification:");
    assertThat(policy.analysisMaxLongEdge()).isEqualTo(1024);
    assertThat(policy.analysisWebpQuality()).isEqualTo(0.80d);
  }

  @Test
  void delegatesAiAndUsesNoExerciseDateForVerifiedPhoto() {
    AiVerificationDecision decision = AiVerificationDecision.builder().approved(true).build();
    when(workoutImageAiPort.analyzeWorkoutPhoto(Path.of("analysis.webp"))).thenReturn(decision);

    AiVerificationDecision result = policy.analyze(workoutImageAiPort, Path.of("analysis.webp"));

    assertThat(result).isEqualTo(decision);
    assertThat(policy.resolveVerifiedExerciseDate(decision)).isNull();
    verify(workoutImageAiPort).analyzeWorkoutPhoto(Path.of("analysis.webp"));
  }
}
