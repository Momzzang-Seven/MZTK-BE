package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotReservationQueryServiceTest {

  @Mock private LoadReservationPort loadReservationPort;

  @InjectMocks private SlotReservationQueryService sut;

  @Test
  @DisplayName("countActiveReservations - returns correct count")
  void countActiveReservations_ReturnsCorrectCount() {
    // Arrange
    Long slotId = 1L;
    int expectedCount = 5;
    given(loadReservationPort.countActiveReservationsBySlotId(slotId)).willReturn(expectedCount);

    // Act
    int actualCount = sut.countActiveReservations(slotId);

    // Assert
    assertThat(actualCount).isEqualTo(expectedCount);
    verify(loadReservationPort).countActiveReservationsBySlotId(slotId);
  }

  @Test
  @DisplayName("hasAnyReservationHistory - returns boolean correctly")
  void hasAnyReservationHistory_ReturnsCorrectly() {
    // Arrange
    Long slotId = 1L;
    given(loadReservationPort.hasAnyReservationHistory(slotId)).willReturn(true);

    // Act
    boolean result = sut.hasAnyReservationHistory(slotId);

    // Assert
    assertThat(result).isTrue();
    verify(loadReservationPort).hasAnyReservationHistory(slotId);
  }
}
