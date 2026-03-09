package momzzangseven.mztkbe.modules.verification.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationAnalysisRequestedEventTest {

  @Test
  void exposesPersistedVerificationRequestId() {
    VerificationAnalysisRequestedEvent event = new VerificationAnalysisRequestedEvent(42L);

    assertThat(event.verificationRequestId()).isEqualTo(42L);
  }
}
