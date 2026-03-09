package momzzangseven.mztkbe.modules.verification.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationStatusTest {

  @Test
  void activeStatusesAreMarkedActive() {
    assertThat(VerificationStatus.PENDING.isActive()).isTrue();
    assertThat(VerificationStatus.ANALYZING.isActive()).isTrue();
    assertThat(VerificationStatus.RETRY_SCHEDULED.isActive()).isTrue();

    assertThat(VerificationStatus.VERIFIED.isActive()).isFalse();
    assertThat(VerificationStatus.REJECTED.isActive()).isFalse();
    assertThat(VerificationStatus.FAILED_FINAL.isActive()).isFalse();
  }

  @Test
  void terminalStatusesAreMarkedTerminal() {
    assertThat(VerificationStatus.VERIFIED.isTerminal()).isTrue();
    assertThat(VerificationStatus.REJECTED.isTerminal()).isTrue();
    assertThat(VerificationStatus.FAILED_FINAL.isTerminal()).isTrue();

    assertThat(VerificationStatus.PENDING.isTerminal()).isFalse();
    assertThat(VerificationStatus.ANALYZING.isTerminal()).isFalse();
    assertThat(VerificationStatus.RETRY_SCHEDULED.isTerminal()).isFalse();
  }

  @Test
  void onlySupportedTransitionsAreAllowed() {
    assertThat(VerificationStatus.PENDING.canTransitionTo(VerificationStatus.ANALYZING)).isTrue();
    assertThat(VerificationStatus.ANALYZING.canTransitionTo(VerificationStatus.VERIFIED)).isTrue();
    assertThat(VerificationStatus.ANALYZING.canTransitionTo(VerificationStatus.REJECTED)).isTrue();
    assertThat(VerificationStatus.ANALYZING.canTransitionTo(VerificationStatus.RETRY_SCHEDULED))
        .isTrue();
    assertThat(VerificationStatus.RETRY_SCHEDULED.canTransitionTo(VerificationStatus.ANALYZING))
        .isTrue();

    assertThat(VerificationStatus.VERIFIED.canTransitionTo(VerificationStatus.PENDING)).isFalse();
    assertThat(VerificationStatus.REJECTED.canTransitionTo(VerificationStatus.ANALYZING)).isFalse();
    assertThat(VerificationStatus.FAILED_FINAL.canTransitionTo(VerificationStatus.ANALYZING))
        .isFalse();
    assertThat(VerificationStatus.PENDING.canTransitionTo(VerificationStatus.VERIFIED)).isFalse();
  }
}
