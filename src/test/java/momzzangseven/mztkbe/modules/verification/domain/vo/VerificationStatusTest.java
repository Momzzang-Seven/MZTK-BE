package momzzangseven.mztkbe.modules.verification.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationStatusTest {

  @Test
  void hasExpectedStatuses() {
    assertThat(VerificationStatus.values())
        .contains(
            VerificationStatus.PENDING,
            VerificationStatus.ANALYZING,
            VerificationStatus.VERIFIED,
            VerificationStatus.REJECTED,
            VerificationStatus.FAILED);
  }
}
