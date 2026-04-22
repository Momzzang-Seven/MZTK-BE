package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoCancelReservationService 단위 테스트")
class AutoCancelReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private AutoCancelBatchItemProcessor itemProcessor;

  @InjectMocks private AutoCancelReservationService sut;

  private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 12, 0);

  private Reservation pendingReservation(Long id) {
    return Reservation.builder()
        .id(id)
        .userId(1L)
        .trainerId(10L)
        .slotId(1L)
        .reservationDate(LocalDate.of(2024, 6, 1))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .orderId("order-" + id)
        .version(0L)
        .build();
  }

  @Nested
  @DisplayName("runBatch() — 성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[AC-01] 후보 없을 때 처리 건수 0 반환 및 itemProcessor 미호출")
    void 후보_없음() {
      // given
      given(loadReservationPort.findPendingForAutoCancel(any(), any(), anyInt()))
          .willReturn(List.of());

      // when
      int result = sut.runBatch(NOW);

      // then
      assertThat(result).isZero();
      then(itemProcessor).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[AC-02] 후보 3건 모두 성공 시 처리 건수 3 반환")
    void 후보_3건_정상() {
      // given
      List<Reservation> candidates =
          List.of(pendingReservation(1L), pendingReservation(2L), pendingReservation(3L));
      given(loadReservationPort.findPendingForAutoCancel(any(), any(), anyInt()))
          .willReturn(candidates);

      // when
      int result = sut.runBatch(NOW);

      // then
      assertThat(result).isEqualTo(3);
      then(itemProcessor).should(times(3)).process(any());
    }

    @Test
    @DisplayName("[AC-03] 3건 중 1건 처리 실패 시 나머지 2건은 처리되고 처리 건수 2 반환")
    void 부분_실패_격리() {
      // given: r2만 on-chain 실패, r1/r3 정상
      Reservation r1 = pendingReservation(1L);
      Reservation r2 = pendingReservation(2L);
      Reservation r3 = pendingReservation(3L);
      given(loadReservationPort.findPendingForAutoCancel(any(), any(), anyInt()))
          .willReturn(List.of(r1, r2, r3));
      // r1, r3: 정상, r2: 예외
      willDoNothing().given(itemProcessor).process(r1);
      willThrow(new RuntimeException("on-chain error")).given(itemProcessor).process(r2);
      willDoNothing().given(itemProcessor).process(r3);

      // when
      int result = sut.runBatch(NOW);

      // then: r2 실패해도 r1, r3 처리 완료 → 2건
      assertThat(result).isEqualTo(2);
      then(itemProcessor).should(times(3)).process(any());
    }
  }
}
