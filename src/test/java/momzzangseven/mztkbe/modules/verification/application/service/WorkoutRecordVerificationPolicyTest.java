package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkoutRecordVerificationPolicyTest {

  @Mock private WorkoutImageAiPort workoutImageAiPort;

  private final WorkoutRecordVerificationPolicy policy = new WorkoutRecordVerificationPolicy();

  @Test
  void allowsExpectedExtensionsAndRejectsOthers() {
    assertThat(policy.allowsExtension("jpg")).isTrue();
    assertThat(policy.allowsExtension("jpeg")).isTrue();
    assertThat(policy.allowsExtension("png")).isTrue();
    assertThat(policy.allowsExtension("heic")).isTrue();
    assertThat(policy.allowsExtension("heif")).isTrue();
    assertThat(policy.allowsExtension("gif")).isFalse();
  }

  @Test
  void exposesRecordPolicySpec() {
    assertThat(policy.kind()).isEqualTo(VerificationKind.WORKOUT_RECORD);
    assertThat(policy.requiresExifValidation()).isFalse();
    assertThat(policy.sourceRefPrefix()).isEqualTo("workout-record-verification:");
    assertThat(policy.analysisMaxLongEdge()).isEqualTo(1536);
    assertThat(policy.analysisWebpQuality()).isEqualTo(0.85d);
  }

  @Test
  void delegatesAiAndUsesExerciseDateFromDecision() {
    AiVerificationDecision decision =
        AiVerificationDecision.builder()
            .approved(true)
            .exerciseDate(LocalDate.of(2026, 3, 13))
            .build();
    when(workoutImageAiPort.analyzeWorkoutRecord(Path.of("analysis.webp"))).thenReturn(decision);

    AiVerificationDecision result = policy.analyze(workoutImageAiPort, Path.of("analysis.webp"));

    assertThat(result).isEqualTo(decision);
    assertThat(policy.resolveVerifiedExerciseDate(decision)).isEqualTo(LocalDate.of(2026, 3, 13));
    verify(workoutImageAiPort).analyzeWorkoutRecord(Path.of("analysis.webp"));
  }
}
