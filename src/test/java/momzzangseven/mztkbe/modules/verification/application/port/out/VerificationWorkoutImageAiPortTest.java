package momzzangseven.mztkbe.modules.verification.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import momzzangseven.mztkbe.modules.verification.domain.vo.AppProvider;
import org.junit.jupiter.api.Test;

class VerificationWorkoutImageAiPortTest {

  @Test
  void photoAnalysisResultExposesAiVerdictFields() {
    WorkoutImageAiPort.PhotoAnalysisResult result =
        new WorkoutImageAiPort.PhotoAnalysisResult(
            true,
            BigDecimal.valueOf(0.94),
            "photo-model-v1",
            List.of("runner visible", "workout context"));

    assertThat(result.workoutPhoto()).isTrue();
    assertThat(result.confidenceScore()).isEqualByComparingTo("0.94");
    assertThat(result.modelName()).isEqualTo("photo-model-v1");
    assertThat(result.reasons()).containsExactly("runner visible", "workout context");
  }

  @Test
  void recordAnalysisResultExposesAiVerdictFields() {
    WorkoutImageAiPort.RecordAnalysisResult result =
        new WorkoutImageAiPort.RecordAnalysisResult(
            true,
            true,
            "2026-03-08",
            AppProvider.NIKE_RUN,
            BigDecimal.valueOf(0.88),
            "record-model-v1",
            List.of("date visible", "pace graph detected"));

    assertThat(result.workoutRecord()).isTrue();
    assertThat(result.dateVisible()).isTrue();
    assertThat(result.exerciseDate()).isEqualTo("2026-03-08");
    assertThat(result.appProvider()).isEqualTo(AppProvider.NIKE_RUN);
    assertThat(result.confidenceScore()).isEqualByComparingTo("0.88");
    assertThat(result.modelName()).isEqualTo("record-model-v1");
    assertThat(result.reasons()).containsExactly("date visible", "pace graph detected");
  }
}
