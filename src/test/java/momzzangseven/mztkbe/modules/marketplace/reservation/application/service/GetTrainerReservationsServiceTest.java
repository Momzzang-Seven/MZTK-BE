package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTrainerReservationsServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadClassSummaryPort loadClassSummaryPort;
  @Mock private LoadUserSummaryPort loadUserSummaryPort;

  @InjectMocks private GetTrainerReservationsService sut;

  private Reservation sampleReservation(Long trainerId) {
    return Reservation.builder()
        .id(10L)
        .userId(1L)
        .trainerId(trainerId)
        .slotId(3L)
        .reservationDate(LocalDate.of(2025, 6, 1))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .orderId("order-abc")
        .txHash("tx-abc")
        .build();
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - 상태 필터 없이 전체 목록 반환")
  void execute_NoStatusFilter_ReturnsAll() {
    // given
    List<Reservation> reservations = List.of(sampleReservation(2L), sampleReservation(2L));
    given(loadReservationPort.findByTrainerId(2L, null)).willReturn(reservations);
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    List<ReservationSummaryResult> results = sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then
    assertThat(results).hasSize(2);
    assertThat(results.get(0).trainerId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - APPROVED 상태 필터 적용")
  void execute_ApprovedStatusFilter_ReturnsFilteredList() {
    // given
    Reservation approved =
        sampleReservation(2L).toBuilder().status(ReservationStatus.APPROVED).build();
    given(loadReservationPort.findByTrainerId(2L, ReservationStatus.APPROVED))
        .willReturn(List.of(approved));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    List<ReservationSummaryResult> results =
        sut.execute(new GetTrainerReservationsQuery(2L, ReservationStatus.APPROVED));

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).status()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - 수강 신청이 없으면 빈 리스트 반환")
  void execute_NoReservations_ReturnsEmptyList() {
    // given
    given(loadReservationPort.findByTrainerId(2L, null)).willReturn(List.of());
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    List<ReservationSummaryResult> results = sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then
    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - trainerId가 null이면 IllegalArgumentException")
  void execute_NullTrainerId_ThrowsIllegalArgument() {
    assertThatThrownBy(() -> sut.execute(new GetTrainerReservationsQuery(null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - classSummary와 trainerSummary가 모두 있으면 enrichment 필드에 값이 채워진다")
  void execute_EnrichmentPresent_FieldsPopulated() {
    // given
    Reservation reservation = sampleReservation(2L);
    ClassSummary classSummary = new ClassSummary("스트레칭 클래스", 30000, "thumb/key.jpg");
    UserSummary trainerSummary = new UserSummary(2L, "trainer-nick");

    given(loadReservationPort.findByTrainerId(2L, null)).willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(List.of(3L))).willReturn(Map.of(3L, classSummary));
    given(loadUserSummaryPort.findByIds(List.of(2L))).willReturn(Map.of(2L, trainerSummary));

    // when
    List<ReservationSummaryResult> results = sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then — enrichment fields must be populated
    assertThat(results).hasSize(1);
    assertThat(results.get(0).classTitle()).isEqualTo("스트레칭 클래스");
    assertThat(results.get(0).trainerNickname()).isEqualTo("trainer-nick");
    assertThat(results.get(0).thumbnailFinalObjectKey()).isEqualTo("thumb/key.jpg");
  }
}
