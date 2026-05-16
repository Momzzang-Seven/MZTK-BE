package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RepairReservationChainReadUseCase;
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
class GetUserReservationsServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadClassSummaryPort loadClassSummaryPort;
  @Mock private LoadUserSummaryPort loadUserSummaryPort;

  @InjectMocks private GetUserReservationsService sut;

  private Reservation sampleReservation(Long userId) {
    return Reservation.builder()
        .id(10L)
        .userId(userId)
        .trainerId(2L)
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
  @DisplayName("내 예약 목록 조회 - 상태 필터 없이 전체 목록 반환")
  void execute_NoStatusFilter_ReturnsAll() {
    // given
    List<Reservation> reservations = List.of(sampleReservation(1L), sampleReservation(1L));
    given(loadReservationPort.findByUserIdCursor(any(), any(), any())).willReturn(reservations);
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, null));

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).userId()).isEqualTo(1L);
    assertThat(result.hasNext()).isFalse();
  }

  @Test
  @DisplayName("내 예약 목록 조회 - PENDING 상태 필터 적용 시 해당 목록만 반환")
  void execute_PendingStatusFilter_ReturnsFilteredList() {
    // given
    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(sampleReservation(1L)));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, ReservationStatus.PENDING));

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).status()).isEqualTo(ReservationStatus.PENDING);
  }

  @Test
  @DisplayName("내 예약 목록 조회 - chain read repair 결과를 목록 응답 매핑 전에 반영한다")
  void execute_AppliesChainReadRepairBeforeMapping() {
    Reservation original =
        sampleReservation(1L).toBuilder().status(ReservationStatus.DEADLINE_SYNC_REQUIRED).build();
    Reservation repaired = original.toBuilder().status(ReservationStatus.DEADLINE_REFUNDED).build();
    RepairReservationChainReadUseCase repairUseCase = mock(RepairReservationChainReadUseCase.class);
    GetUserReservationsService repairingSut =
        new GetUserReservationsService(
            loadReservationPort, loadClassSummaryPort, loadUserSummaryPort, null, repairUseCase);
    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(original));
    given(repairUseCase.repairBatch(List.of(original))).willReturn(List.of(repaired));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    CursorSlice<ReservationSummaryResult> result =
        repairingSut.execute(new GetUserReservationsQuery(1L, null));

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().status()).isEqualTo(ReservationStatus.DEADLINE_REFUNDED);
    then(repairUseCase).should().repairBatch(List.of(original));
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 예약이 없으면 빈 리스트 반환")
  void execute_NoReservations_ReturnsEmptyList() {
    // given
    given(loadReservationPort.findByUserIdCursor(any(), any(), any())).willReturn(List.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, null));

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  @DisplayName("내 예약 목록 조회 - userId가 null이면 IllegalArgumentException")
  void execute_NullUserId_ThrowsIllegalArgument() {
    assertThatThrownBy(() -> sut.execute(new GetUserReservationsQuery(null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("내 예약 목록 조회 - classSummary와 trainerSummary가 모두 있으면 enrichment 필드에 값이 채워진다")
  void execute_EnrichmentPresent_FieldsPopulated() {
    // given
    Reservation reservation = sampleReservation(1L);
    ClassSummary classSummary = new ClassSummary("필라테스 입문", 45000, "thumb/pilates.jpg");
    UserSummary trainerSummary = new UserSummary(2L, "trainer-nick");

    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(List.of(3L))).willReturn(Map.of(3L, classSummary));
    given(loadUserSummaryPort.findByIds(List.of(2L))).willReturn(Map.of(2L, trainerSummary));

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, null));

    // then — enrichment fields must be populated
    assertThat(result.items()).hasSize(1);
    // reservation has bookedPriceAmount == 0 (legacy) → falls back to adapter title
    assertThat(result.items().get(0).classTitle()).isEqualTo("필라테스 입문");
    assertThat(result.items().get(0).trainerNickname()).isEqualTo("trainer-nick");
    assertThat(result.items().get(0).thumbnailFinalObjectKey()).isEqualTo("thumb/pilates.jpg");
    // userNickname is intentionally null on the user-list path (not needed for self-view)
    assertThat(result.items().get(0).userNickname()).isNull();
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 스냅샷이 있으면 클래스가 비활성화되어도 title이 유지된다")
  void execute_SnapshotUsed_WhenClassInactive() {
    // given — reservation with snapshot fields set (bookedPriceAmount != null)
    Reservation reservation =
        sampleReservation(1L).toBuilder()
            .bookedPriceAmount(50000)
            .bookedClassTitle("필라테스 심화")
            .build();

    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    // Adapter returns empty — simulates class being inactive / deactivated
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, null));

    // then — title must come from the snapshot, not the (empty) adapter result
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).classTitle()).isEqualTo("필라테스 심화");
    assertThat(result.items().get(0).thumbnailFinalObjectKey()).isNull();
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 스냅샷(bookedPriceAmount != null)이 있으면 snapshot title/price 우선 사용")
  void execute_SnapshotPresent_UsesSnapshotValues() {
    // given — reservation with a non-null snapshot
    Reservation reservation =
        sampleReservation(1L).toBuilder()
            .bookedPriceAmount(35000)
            .bookedClassTitle("요가 기초")
            .build();
    // adapter also has a live value; snapshot must win
    ClassSummary adapterSummary = new ClassSummary("요가 심화 (최신)", 40000, "thumb/yoga.jpg");

    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of(3L, adapterSummary));
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, null));

    // then — snapshot title/price wins; only thumbnail comes from adapter
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).classTitle()).isEqualTo("요가 기초");
    assertThat(result.items().get(0).priceAmount()).isEqualTo(35000);
    assertThat(result.items().get(0).thumbnailFinalObjectKey()).isEqualTo("thumb/yoga.jpg");
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 레거시 레코드(bookedPriceAmount == null)는 어댑터 fallback 사용")
  void execute_LegacyRecord_FallsBackToAdapter() {
    // given — reservation with null snapshot (legacy: pre-V065)
    Reservation reservation = sampleReservation(1L); // bookedPriceAmount == null (default builder)
    ClassSummary adapterSummary = new ClassSummary("레거시 클래스", 25000, "thumb/legacy.jpg");

    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of(3L, adapterSummary));
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, null));

    // then — title and price come from adapter (live cross-module lookup)
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).classTitle()).isEqualTo("레거시 클래스");
    assertThat(result.items().get(0).priceAmount()).isEqualTo(25000);
    assertThat(result.items().get(0).thumbnailFinalObjectKey()).isEqualTo("thumb/legacy.jpg");
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 레거시 레코드이고 클래스도 없으면 priceAmount는 null")
  void execute_LegacyRecordNoAdapter_PriceAmountIsNull() {
    // given — legacy record + adapter returns empty (class deactivated)
    Reservation reservation = sampleReservation(1L); // bookedPriceAmount == null

    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, null));

    // then — no snapshot, no adapter data → priceAmount is null (not 0)
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).classTitle()).isNull();
    assertThat(result.items().get(0).priceAmount()).isNull();
  }

  @Test
  @DisplayName(
      "내 예약 목록 조회 - bookedPriceAmount만 있고 bookedClassTitle이 null인 partial snapshot은 어댑터 fallback 사용")
  void execute_PartialSnapshot_PriceOnlyFallsBackToAdapter() {
    // given — partial snapshot: priceAmount set but classTitle null
    Reservation reservation =
        sampleReservation(1L).toBuilder()
            .bookedPriceAmount(35000)
            // bookedClassTitle intentionally NOT set (null)
            .build();
    ClassSummary adapterSummary = new ClassSummary("어댑터 클래스 제목", 35000, "thumb/adapter.jpg");

    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of(3L, adapterSummary));
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetUserReservationsQuery(1L, null));

    // then — partial snapshot triggers live fallback; classTitle must NOT be null
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).classTitle()).isEqualTo("어댑터 클래스 제목");
    assertThat(result.items().get(0).priceAmount()).isEqualTo(35000);
  }

  @Test
  @DisplayName("내 예약 목록 조회 - status 없이 조회한 cursor는 status 필터가 있는 요청에서 InvalidCursorException 발생")
  void execute_CursorScopeMismatch_ThrowsInvalidCursorException() {
    // given — encode a cursor with scope "user-reservations:ALL" (no status)
    momzzangseven.mztkbe.global.pagination.KeysetCursor allCursor =
        new momzzangseven.mztkbe.global.pagination.KeysetCursor(
            java.time.LocalDateTime.of(2025, 6, 1, 10, 0),
            10L,
            GetUserReservationsQuery.cursorScope(null)); // "user-reservations:ALL"
    String encodedAllCursor = momzzangseven.mztkbe.global.pagination.CursorCodec.encode(allCursor);

    // when — try to decode that cursor with an APPROVED-scoped page request
    // then — scope mismatch → InvalidCursorException
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                momzzangseven.mztkbe.global.pagination.CursorPageRequest.of(
                    encodedAllCursor,
                    20,
                    20,
                    100,
                    GetUserReservationsQuery.cursorScope(ReservationStatus.APPROVED)))
        .isInstanceOf(momzzangseven.mztkbe.global.error.pagination.InvalidCursorException.class);
  }

  @Test
  @DisplayName("내 예약 목록 조회 - status가 다른 두 cursorScope 값은 서로 달라야 한다")
  void cursorScope_DifferentStatuses_ProduceDifferentScopes() {
    String allScope = GetUserReservationsQuery.cursorScope(null);
    String approvedScope = GetUserReservationsQuery.cursorScope(ReservationStatus.APPROVED);
    String pendingScope = GetUserReservationsQuery.cursorScope(ReservationStatus.PENDING);

    assertThat(allScope).isNotEqualTo(approvedScope);
    assertThat(allScope).isNotEqualTo(pendingScope);
    assertThat(approvedScope).isNotEqualTo(pendingScope);
    // same status always produces the same scope
    assertThat(GetUserReservationsQuery.cursorScope(ReservationStatus.APPROVED))
        .isEqualTo(approvedScope);
  }
}
