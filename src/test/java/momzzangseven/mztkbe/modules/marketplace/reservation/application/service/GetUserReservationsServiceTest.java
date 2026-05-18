package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RepairReservationChainReadUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetUserReservationsServiceTest {

  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadClassSummaryPort loadClassSummaryPort;
  @Mock private LoadUserSummaryPort loadUserSummaryPort;

  private GetUserReservationsService sut;

  @BeforeEach
  void setUp() {
    sut =
        new GetUserReservationsService(
            loadReservationPort,
            loadClassSummaryPort,
            loadUserSummaryPort,
            emptyResumePort(),
            noOpRepairUseCase(),
            TEST_CLOCK);
  }

  private static LoadReservationExecutionResumePort emptyResumePort() {
    return new LoadReservationExecutionResumePort() {
      @Override
      public Optional<ReservationExecutionResumeView> loadLatest(Long reservationId) {
        return Optional.empty();
      }

      @Override
      public Map<Long, ReservationExecutionResumeView> loadLatestBatch(
          Collection<Long> reservationIds) {
        return Map.of();
      }
    };
  }

  private static RepairReservationChainReadUseCase noOpRepairUseCase() {
    return new RepairReservationChainReadUseCase() {
      @Override
      public Reservation repairOne(Reservation reservation) {
        return reservation;
      }

      @Override
      public List<Reservation> repairBatch(List<Reservation> reservations) {
        return reservations;
      }
    };
  }

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
    assertThat(result.items().get(0).status()).isEqualTo(ReservationDisplayStatus.PENDING);
    assertThat(result.items().get(0).businessStatus()).isEqualTo(ReservationStatus.PENDING);
  }

  @Test
  @DisplayName("내 예약 목록 조회 - PURCHASE_PENDING 필터는 HOLDING 저장 row를 display status로 재필터링한다")
  void execute_PurchasePendingFilter_UsesHoldingQueryAndDisplayStatus() {
    Reservation preparing =
        sampleReservation(1L).toBuilder()
            .id(11L)
            .status(ReservationStatus.HOLDING)
            .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
            .build();
    Reservation pending =
        sampleReservation(1L).toBuilder()
            .id(12L)
            .status(ReservationStatus.HOLDING)
            .escrowStatus(ReservationEscrowStatus.PURCHASE_PENDING)
            .currentExecutionIntentPublicId("intent-1")
            .build();
    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(preparing, pending));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    CursorSlice<ReservationSummaryResult> result =
        sut.execute(
            new GetUserReservationsQuery(
                1L,
                ReservationListStatusFilter.PURCHASE_PENDING,
                CursorPageRequest.of(
                    null,
                    null,
                    20,
                    100,
                    GetUserReservationsQuery.cursorScope(
                        ReservationListStatusFilter.PURCHASE_PENDING))));

    assertThat(result.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.reservationId()).isEqualTo(12L);
              assertThat(item.status()).isEqualTo(ReservationDisplayStatus.PURCHASE_PENDING);
              assertThat(item.businessStatus()).isNull();
            });
    then(loadReservationPort)
        .should()
        .findByUserIdCursor(eq(1L), eq(ReservationStatus.HOLDING), any());
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
            loadReservationPort,
            loadClassSummaryPort,
            loadUserSummaryPort,
            null,
            repairUseCase,
            TEST_CLOCK);
    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(original));
    given(repairUseCase.repairBatch(List.of(original))).willReturn(List.of(repaired));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    CursorSlice<ReservationSummaryResult> result =
        repairingSut.execute(new GetUserReservationsQuery(1L, null));

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().status())
        .isEqualTo(ReservationDisplayStatus.DEADLINE_REFUNDED);
    assertThat(result.items().getFirst().businessStatus())
        .isEqualTo(ReservationStatus.DEADLINE_REFUNDED);
    then(repairUseCase).should().repairBatch(List.of(original));
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 최신 web3 execution summary를 hydrate하고 viewer recover flag를 계산한다")
  void execute_HydratesWeb3ExecutionSummary() {
    Reservation reservation =
        sampleReservation(1L).toBuilder()
            .currentExecutionIntentPublicId("intent-1")
            .status(ReservationStatus.PURCHASE_PENDING)
            .build();
    LoadReservationExecutionResumePort resumePort = mock(LoadReservationExecutionResumePort.class);
    GetUserReservationsService hydratingSut =
        new GetUserReservationsService(
            loadReservationPort,
            loadClassSummaryPort,
            loadUserSummaryPort,
            resumePort,
            null,
            TEST_CLOCK);
    ReservationExecutionResumeView resumeView =
        resumeView(
            "MARKETPLACE_CLASS_PURCHASE",
            "PENDING_ONCHAIN",
            new ReservationExecutionResumeView.Transaction(99L, "SUCCEEDED", "0xtx"));
    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());
    given(resumePort.loadLatestBatch(List.of(10L))).willReturn(Map.of(10L, resumeView));

    CursorSlice<ReservationSummaryResult> result =
        hydratingSut.execute(new GetUserReservationsQuery(1L, null));

    ReservationExecutionResumeView hydrated = result.items().getFirst().web3Execution();
    assertThat(hydrated).isNotNull();
    assertThat(hydrated.viewerAction()).isEqualTo("PURCHASE");
    assertThat(hydrated.viewerCanExecute()).isFalse();
    assertThat(hydrated.viewerCanRecover()).isTrue();
    assertThat(hydrated.transaction().id()).isEqualTo(99L);
    assertThat(hydrated.transaction().status()).isEqualTo("SUCCEEDED");
    assertThat(hydrated.transaction().txHash()).isEqualTo("0xtx");
    assertThat(result.items().getFirst().viewerActions().viewerAction()).isEqualTo("PURCHASE");
    assertThat(result.items().getFirst().viewerActions().viewerCanRecover()).isTrue();
    then(resumePort).should().loadLatestBatch(List.of(10L));
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 상태 필터가 있으면 chain repair 이후에도 필터 조건을 유지한다")
  void execute_StatusFilter_RemovesRowsChangedByChainReadRepair() {
    Reservation original =
        sampleReservation(1L).toBuilder().status(ReservationStatus.DEADLINE_SYNC_REQUIRED).build();
    Reservation repaired = original.toBuilder().status(ReservationStatus.DEADLINE_REFUNDED).build();
    RepairReservationChainReadUseCase repairUseCase = mock(RepairReservationChainReadUseCase.class);
    GetUserReservationsService repairingSut =
        new GetUserReservationsService(
            loadReservationPort,
            loadClassSummaryPort,
            loadUserSummaryPort,
            null,
            repairUseCase,
            TEST_CLOCK);
    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(original));
    given(repairUseCase.repairBatch(List.of(original))).willReturn(List.of(repaired));

    CursorSlice<ReservationSummaryResult> result =
        repairingSut.execute(
            new GetUserReservationsQuery(1L, ReservationStatus.DEADLINE_SYNC_REQUIRED));

    assertThat(result.items()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    then(repairUseCase).should().repairBatch(List.of(original));
  }

  @Test
  @DisplayName("내 예약 목록 조회 - repair 후 필터링으로 페이지가 비면 다음 raw page에서 보충한다")
  void execute_StatusFilter_SupplementsPageAfterRepairFiltering() {
    ReservationStatus status = ReservationStatus.DEADLINE_SYNC_REQUIRED;
    Reservation first =
        sampleReservation(1L).toBuilder()
            .id(30L)
            .reservationDate(LocalDate.of(2025, 6, 3))
            .status(status)
            .build();
    Reservation second =
        sampleReservation(1L).toBuilder()
            .id(20L)
            .reservationDate(LocalDate.of(2025, 6, 2))
            .status(status)
            .build();
    Reservation third =
        sampleReservation(1L).toBuilder()
            .id(10L)
            .reservationDate(LocalDate.of(2025, 6, 1))
            .status(status)
            .build();
    Reservation repairedFirst =
        first.toBuilder().status(ReservationStatus.DEADLINE_REFUNDED).build();
    RepairReservationChainReadUseCase repairUseCase = mock(RepairReservationChainReadUseCase.class);
    GetUserReservationsService repairingSut =
        new GetUserReservationsService(
            loadReservationPort,
            loadClassSummaryPort,
            loadUserSummaryPort,
            null,
            repairUseCase,
            TEST_CLOCK);
    CursorPageRequest pageRequest =
        CursorPageRequest.of(
            null,
            1,
            20,
            100,
            GetUserReservationsQuery.cursorScope(
                ReservationListStatusFilter.valueOf(status.name())));
    given(loadReservationPort.findByUserIdCursor(any(), any(), any()))
        .willReturn(List.of(first, second), List.of(third));
    given(repairUseCase.repairBatch(List.of(first, second)))
        .willReturn(List.of(repairedFirst, second));
    given(repairUseCase.repairBatch(List.of(third))).willReturn(List.of(third));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    CursorSlice<ReservationSummaryResult> result =
        repairingSut.execute(new GetUserReservationsQuery(1L, status, pageRequest));

    assertThat(result.items())
        .singleElement()
        .satisfies(item -> assertThat(item.reservationId()).isEqualTo(20L));
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isNotBlank();
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
            GetUserReservationsQuery.cursorScope(
                (ReservationListStatusFilter) null)); // "user-reservations:ALL"
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
                    GetUserReservationsQuery.cursorScope(ReservationListStatusFilter.APPROVED)))
        .isInstanceOf(momzzangseven.mztkbe.global.error.pagination.InvalidCursorException.class);
  }

  @Test
  @DisplayName("내 예약 목록 조회 - status가 다른 두 cursorScope 값은 서로 달라야 한다")
  void cursorScope_DifferentStatuses_ProduceDifferentScopes() {
    String allScope = GetUserReservationsQuery.cursorScope((ReservationListStatusFilter) null);
    String approvedScope =
        GetUserReservationsQuery.cursorScope(ReservationListStatusFilter.APPROVED);
    String pendingScope = GetUserReservationsQuery.cursorScope(ReservationListStatusFilter.PENDING);

    assertThat(allScope).isNotEqualTo(approvedScope);
    assertThat(allScope).isNotEqualTo(pendingScope);
    assertThat(approvedScope).isNotEqualTo(pendingScope);
    // same status always produces the same scope
    assertThat(GetUserReservationsQuery.cursorScope(ReservationListStatusFilter.APPROVED))
        .isEqualTo(approvedScope);
  }

  private ReservationExecutionResumeView resumeView(
      String actionType,
      String intentStatus,
      ReservationExecutionResumeView.Transaction transaction) {
    return new ReservationExecutionResumeView(
        new ReservationExecutionResumeView.Resource("ORDER", "10", "PENDING_EXECUTION"),
        actionType,
        new ReservationExecutionResumeView.ExecutionIntent(
            "intent-1", intentStatus, java.time.LocalDateTime.of(2026, 5, 18, 10, 0), 1_800L),
        new ReservationExecutionResumeView.Execution("EIP7702", 2),
        transaction);
  }
}
