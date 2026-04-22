package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoSettleReservationService 단위 테스트")
class AutoSettleReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private AutoSettleBatchItemProcessor itemProcessor;

  @InjectMocks private AutoSettleReservationService sut;

  private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 2, 12, 0);

  private Reservation approvedReservation(Long id) {
    return Reservation.builder()
        .id(id)
        .userId(1L)
        .trainerId(10L)
        .slotId(1L)
        .reservationDate(LocalDate.of(2024, 5, 31))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.APPROVED)
        .orderId("order-" + id)
        .txHash("0xABC")
        .version(1L)
        .build();
  }

  @Nested
  @DisplayName("runBatch() — 성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[AS-01] 후보 없을 때 처리 건수 0 반환 및 itemProcessor 미호출")
    void 후보_없음() {
      // given
      given(loadReservationPort.findApprovedForAutoSettle(any(), anyInt())).willReturn(List.of());

      // when
      int result = sut.runBatch(NOW);

      // then
      assertThat(result).isZero();
      then(itemProcessor).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[AS-02] 후보 2건 모두 성공 시 처리 건수 2 반환")
    void 후보_2건_정상() {
      // given
      List<Reservation> candidates = List.of(approvedReservation(1L), approvedReservation(2L));
      given(loadReservationPort.findApprovedForAutoSettle(any(), anyInt())).willReturn(candidates);

      // when
      int result = sut.runBatch(NOW);

      // then
      assertThat(result).isEqualTo(2);
      then(itemProcessor).should(times(2)).process(any());
    }

    @Test
    @DisplayName("[AS-03] 1건 처리 실패 시 나머지는 계속 처리되고 처리 건수 1 반환")
    void 부분_실패_격리() {
      // given
      Reservation r1 = approvedReservation(1L);
      Reservation r2 = approvedReservation(2L);
      given(loadReservationPort.findApprovedForAutoSettle(any(), anyInt()))
          .willReturn(List.of(r1, r2));
      willThrow(new RuntimeException("settle error")).given(itemProcessor).process(eq(r1));

      // when
      int result = sut.runBatch(NOW);

      // then: r1 실패 → r2만 성공 → 1건
      assertThat(result).isEqualTo(1);
      then(itemProcessor).should(times(2)).process(any());
    }
  }
}
