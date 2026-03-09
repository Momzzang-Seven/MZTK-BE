package momzzangseven.mztkbe.modules.verification.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationPropertiesTest {

  @Test
  void exposesExpectedDefaultThresholdsAndWindows() {
    VerificationProperties properties = new VerificationProperties();

    assertThat(properties.getAnalysis().getWorkoutPhotoConfidenceThreshold()).isEqualTo(0.70);
    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
    assertThat(properties.getRetry().getFirstBackoffMinutes()).isEqualTo(1);
    assertThat(properties.getRetry().getSecondBackoffMinutes()).isEqualTo(5);
    assertThat(properties.getRetry().getThirdBackoffMinutes()).isEqualTo(15);
    assertThat(properties.getRecovery().getPendingStaleMinutes()).isEqualTo(5);
    assertThat(properties.getRecovery().getAnalyzingStaleMinutes()).isEqualTo(10);
  }

  @Test
  void allowsNestedPolicyOverrides() {
    VerificationProperties properties = new VerificationProperties();

    properties.getAnalysis().setWorkoutPhotoConfidenceThreshold(0.85);
    properties.getRetry().setMaxAttempts(4);
    properties.getRetry().setFirstBackoffMinutes(2);
    properties.getRetry().setSecondBackoffMinutes(6);
    properties.getRetry().setThirdBackoffMinutes(20);
    properties.getRecovery().setPendingStaleMinutes(7);
    properties.getRecovery().setAnalyzingStaleMinutes(11);

    assertThat(properties.getAnalysis().getWorkoutPhotoConfidenceThreshold()).isEqualTo(0.85);
    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(4);
    assertThat(properties.getRetry().getFirstBackoffMinutes()).isEqualTo(2);
    assertThat(properties.getRetry().getSecondBackoffMinutes()).isEqualTo(6);
    assertThat(properties.getRetry().getThirdBackoffMinutes()).isEqualTo(20);
    assertThat(properties.getRecovery().getPendingStaleMinutes()).isEqualTo(7);
    assertThat(properties.getRecovery().getAnalyzingStaleMinutes()).isEqualTo(11);
  }
}
