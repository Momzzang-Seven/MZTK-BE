package momzzangseven.mztkbe.modules.verification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.Test;

class VerificationRequestTest {

  @Test
  void activeRequestsDelegateToStatusFlags() {
    VerificationRequest request =
        VerificationRequest.builder().status(VerificationStatus.ANALYZING).build();

    assertThat(request.isActive()).isTrue();
    assertThat(request.isTerminal()).isFalse();
  }

  @Test
  void terminalRequestsDelegateToStatusFlags() {
    VerificationRequest request =
        VerificationRequest.builder().status(VerificationStatus.REJECTED).build();

    assertThat(request.isActive()).isFalse();
    assertThat(request.isTerminal()).isTrue();
  }

  @Test
  void nullStatusIsNeitherActiveNorTerminal() {
    VerificationRequest request = VerificationRequest.builder().status(null).build();

    assertThat(request.isActive()).isFalse();
    assertThat(request.isTerminal()).isFalse();
  }
}
