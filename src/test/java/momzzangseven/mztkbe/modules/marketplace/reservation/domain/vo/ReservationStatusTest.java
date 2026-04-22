package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationStatusTest {

  @Test
  @DisplayName("isTerminal - correctly identifies terminal states")
  void isTerminal_CorrectlyIdentifies() {
    assertThat(ReservationStatus.PENDING.isTerminal()).isFalse();
    assertThat(ReservationStatus.APPROVED.isTerminal()).isFalse();

    assertThat(ReservationStatus.USER_CANCELLED.isTerminal()).isTrue();
    assertThat(ReservationStatus.REJECTED.isTerminal()).isTrue();
    assertThat(ReservationStatus.TIMEOUT_CANCELLED.isTerminal()).isTrue();
    assertThat(ReservationStatus.SETTLED.isTerminal()).isTrue();
    assertThat(ReservationStatus.AUTO_SETTLED.isTerminal()).isTrue();
  }

  @Test
  @DisplayName("isCancellable - correctly identifies cancellable states")
  void isCancellable_CorrectlyIdentifies() {
    assertThat(ReservationStatus.PENDING.isCancellable()).isTrue();

    assertThat(ReservationStatus.APPROVED.isCancellable()).isFalse();
    assertThat(ReservationStatus.USER_CANCELLED.isCancellable()).isFalse();
    assertThat(ReservationStatus.REJECTED.isCancellable()).isFalse();
    assertThat(ReservationStatus.TIMEOUT_CANCELLED.isCancellable()).isFalse();
    assertThat(ReservationStatus.SETTLED.isCancellable()).isFalse();
    assertThat(ReservationStatus.AUTO_SETTLED.isCancellable()).isFalse();
  }

  @Test
  @DisplayName("canTransitionTo - PENDING transitions")
  void canTransitionTo_FromPending() {
    assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.APPROVED)).isTrue();
    assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.USER_CANCELLED))
        .isTrue();
    assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.REJECTED)).isTrue();
    assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.TIMEOUT_CANCELLED))
        .isTrue();

    assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.PENDING)).isFalse();
    assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.SETTLED)).isFalse();
    assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.AUTO_SETTLED)).isFalse();
  }

  @Test
  @DisplayName("canTransitionTo - APPROVED transitions")
  void canTransitionTo_FromApproved() {
    assertThat(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.SETTLED)).isTrue();
    assertThat(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.AUTO_SETTLED)).isTrue();

    assertThat(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.PENDING)).isFalse();
    assertThat(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.APPROVED)).isFalse();
    assertThat(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.USER_CANCELLED))
        .isFalse();
    assertThat(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.REJECTED)).isFalse();
    assertThat(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.TIMEOUT_CANCELLED))
        .isFalse();
  }

  @Test
  @DisplayName("canTransitionTo - terminal states allow no transitions")
  void canTransitionTo_FromTerminalStates() {
    ReservationStatus[] terminalStates = {
      ReservationStatus.USER_CANCELLED,
      ReservationStatus.REJECTED,
      ReservationStatus.TIMEOUT_CANCELLED,
      ReservationStatus.SETTLED,
      ReservationStatus.AUTO_SETTLED
    };

    for (ReservationStatus terminal : terminalStates) {
      for (ReservationStatus target : ReservationStatus.values()) {
        assertThat(terminal.canTransitionTo(target)).isFalse();
      }
    }
  }
}
