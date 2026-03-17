package momzzangseven.mztkbe.modules.verification.config;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.verification.infrastructure.config.VerificationPromptProvider;
import org.junit.jupiter.api.Test;

class VerificationPromptProviderTest {

  @Test
  void loadsPromptTripletsWithoutDrift() {
    VerificationPromptProvider provider = new VerificationPromptProvider();

    assertThat(provider.getWorkoutPhotoSystemInstruction()).isNotBlank();
    assertThat(provider.getWorkoutPhotoUserPrompt().trim())
        .isEqualTo(
            "Analyze the attached normalized workout-photo candidate and return only the result JSON.");
    assertThat(provider.getWorkoutPhotoResponseSchema()).contains("workoutPhoto");

    assertThat(provider.getWorkoutRecordSystemInstruction()).isNotBlank();
    assertThat(provider.getWorkoutRecordUserPrompt().trim())
        .isEqualTo(
            "Analyze the attached normalized workout-record candidate and return only the result JSON.");
    assertThat(provider.getWorkoutRecordResponseSchema()).contains("workoutRecord");
  }
}
