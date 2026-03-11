package momzzangseven.mztkbe.modules.verification.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationPropertiesTest {

  @Test
  void exposesExpectedDefaultThresholds() {
    VerificationProperties properties = new VerificationProperties();

    assertThat(properties.getAnalysis().getWorkoutPhotoConfidenceThreshold()).isEqualTo(0.70);
  }

  @Test
  void allowsAnalysisThresholdOverride() {
    VerificationProperties properties = new VerificationProperties();

    properties.getAnalysis().setWorkoutPhotoConfidenceThreshold(0.85);

    assertThat(properties.getAnalysis().getWorkoutPhotoConfidenceThreshold()).isEqualTo(0.85);
  }
}
