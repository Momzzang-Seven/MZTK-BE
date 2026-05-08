package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
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
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any())).willReturn(reservations);
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    // single-lookup for trainer's own nickname
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    // batch-lookup for booker nicknames
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).trainerId()).isEqualTo(2L);
    assertThat(result.hasNext()).isFalse();
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - APPROVED 상태 필터 적용")
  void execute_ApprovedStatusFilter_ReturnsFilteredList() {
    // given
    Reservation approved =
        sampleReservation(2L).toBuilder().status(ReservationStatus.APPROVED).build();
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(approved));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetTrainerReservationsQuery(2L, ReservationStatus.APPROVED));

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).status()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - 수강 신청이 없으면 빈 리스트 반환")
  void execute_NoReservations_ReturnsEmptyList() {
    // given
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any())).willReturn(List.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
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
    UserSummary userSummary = new UserSummary(1L, "user-nick");

    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(List.of(3L))).willReturn(Map.of(3L, classSummary));
    // single-lookup: trainer's own nickname
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.of(trainerSummary));
    // batch-lookup: booker nicknames
    given(loadUserSummaryPort.findByIds(List.of(1L))).willReturn(Map.of(1L, userSummary));

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then — enrichment fields must be populated
    assertThat(result.items()).hasSize(1);
    // reservation has bookedPriceAmount == 0 (legacy) → falls back to adapter title
    assertThat(result.items().get(0).classTitle()).isEqualTo("스트레칭 클래스");
    assertThat(result.items().get(0).trainerNickname()).isEqualTo("trainer-nick");
    assertThat(result.items().get(0).thumbnailFinalObjectKey()).isEqualTo("thumb/key.jpg");
    // userNickname — required so the trainer can identify who booked
    assertThat(result.items().get(0).userNickname()).isEqualTo("user-nick");
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - 스냅샷이 있으면 클래스가 비활성화되어도 title이 유지된다")
  void execute_SnapshotUsed_WhenClassInactive() {
    // given — reservation with snapshot fields set (bookedPriceAmount > 0)
    Reservation reservation =
        sampleReservation(2L).toBuilder()
            .bookedPriceAmount(35000)
            .bookedClassTitle("스트레칭 심화")
            .build();

    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    // Adapter returns empty — simulates class being inactive / deactivated
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then — title must come from the snapshot, not the (empty) adapter result
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).classTitle()).isEqualTo("스트레칭 심화");
    assertThat(result.items().get(0).thumbnailFinalObjectKey()).isNull();
  }
}
