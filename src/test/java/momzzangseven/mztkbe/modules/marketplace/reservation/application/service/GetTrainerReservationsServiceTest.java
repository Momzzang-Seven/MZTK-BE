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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
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
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTrainerReservationsServiceTest {

  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadClassSummaryPort loadClassSummaryPort;
  @Mock private LoadUserSummaryPort loadUserSummaryPort;

  private GetTrainerReservationsService sut;

  @BeforeEach
  void setUp() {
    sut =
        new GetTrainerReservationsService(
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
    assertThat(result.items().get(0).status()).isEqualTo(ReservationDisplayStatus.APPROVED);
    assertThat(result.items().get(0).businessStatus()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  @DisplayName("트레이너 예약 목록 조회 - PURCHASE_PREPARING 필터는 HOLDING 저장 row를 display status로 재필터링한다")
  void execute_PurchasePreparingFilter_UsesHoldingQueryAndDisplayStatus() {
    Reservation preparing =
        sampleReservation(2L).toBuilder()
            .id(11L)
            .status(ReservationStatus.HOLDING)
            .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
            .build();
    Reservation pending =
        sampleReservation(2L).toBuilder()
            .id(12L)
            .status(ReservationStatus.HOLDING)
            .escrowStatus(ReservationEscrowStatus.PURCHASE_PENDING)
            .currentExecutionIntentPublicId("intent-1")
            .build();
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(preparing, pending));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    CursorSlice<ReservationSummaryResult> result =
        sut.execute(
            new GetTrainerReservationsQuery(
                2L,
                ReservationListStatusFilter.PURCHASE_PREPARING,
                CursorPageRequest.of(
                    null,
                    null,
                    20,
                    100,
                    GetTrainerReservationsQuery.cursorScope(
                        ReservationListStatusFilter.PURCHASE_PREPARING))));

    assertThat(result.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.reservationId()).isEqualTo(11L);
              assertThat(item.status()).isEqualTo(ReservationDisplayStatus.PURCHASE_PREPARING);
              assertThat(item.businessStatus()).isNull();
            });
    then(loadReservationPort)
        .should()
        .findByTrainerIdCursor(eq(2L), eq(ReservationStatus.HOLDING), any());
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - chain read repair 결과를 목록 응답 매핑 전에 반영한다")
  void execute_AppliesChainReadRepairBeforeMapping() {
    Reservation original =
        sampleReservation(2L).toBuilder().status(ReservationStatus.DEADLINE_SYNC_REQUIRED).build();
    Reservation repaired = original.toBuilder().status(ReservationStatus.DEADLINE_REFUNDED).build();
    RepairReservationChainReadUseCase repairUseCase = mock(RepairReservationChainReadUseCase.class);
    GetTrainerReservationsService repairingSut =
        new GetTrainerReservationsService(
            loadReservationPort,
            loadClassSummaryPort,
            loadUserSummaryPort,
            null,
            repairUseCase,
            TEST_CLOCK);
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(original));
    given(repairUseCase.repairBatch(List.of(original))).willReturn(List.of(repaired));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    CursorSlice<ReservationSummaryResult> result =
        repairingSut.execute(new GetTrainerReservationsQuery(2L, null));

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().status())
        .isEqualTo(ReservationDisplayStatus.DEADLINE_REFUNDED);
    assertThat(result.items().getFirst().businessStatus())
        .isEqualTo(ReservationStatus.DEADLINE_REFUNDED);
    then(repairUseCase).should().repairBatch(List.of(original));
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - 최신 web3 execution summary를 hydrate하고 trainer action flag를 계산한다")
  void execute_HydratesWeb3ExecutionSummary() {
    Reservation reservation =
        sampleReservation(2L).toBuilder()
            .currentExecutionIntentPublicId("intent-1")
            .status(ReservationStatus.REJECT_PENDING)
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .build();
    LoadReservationExecutionResumePort resumePort = mock(LoadReservationExecutionResumePort.class);
    GetTrainerReservationsService hydratingSut =
        new GetTrainerReservationsService(
            loadReservationPort,
            loadClassSummaryPort,
            loadUserSummaryPort,
            resumePort,
            null,
            TEST_CLOCK);
    ReservationExecutionResumeView resumeView =
        resumeView(
            "MARKETPLACE_CLASS_CANCEL",
            "AWAITING_SIGNATURE",
            new ReservationExecutionResumeView.Transaction(77L, "PENDING", "0xtx"));
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());
    given(resumePort.loadLatestBatch(List.of(10L))).willReturn(Map.of(10L, resumeView));

    CursorSlice<ReservationSummaryResult> result =
        hydratingSut.execute(new GetTrainerReservationsQuery(2L, null));

    ReservationExecutionResumeView hydrated = result.items().getFirst().web3Execution();
    assertThat(hydrated).isNotNull();
    assertThat(hydrated.viewerAction()).isEqualTo("TRAINER_REJECT");
    assertThat(hydrated.viewerCanExecute()).isTrue();
    assertThat(hydrated.viewerCanRecover()).isFalse();
    assertThat(hydrated.transaction().id()).isEqualTo(77L);
    assertThat(hydrated.transaction().status()).isEqualTo("PENDING");
    assertThat(hydrated.transaction().txHash()).isEqualTo("0xtx");
    assertThat(result.items().getFirst().viewerActions().viewerAction())
        .isEqualTo("TRAINER_REJECT");
    assertThat(result.items().getFirst().viewerActions().viewerCanReject()).isTrue();
    then(resumePort).should().loadLatestBatch(List.of(10L));
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - 상태 필터가 있으면 chain repair 이후에도 필터 조건을 유지한다")
  void execute_StatusFilter_RemovesRowsChangedByChainReadRepair() {
    Reservation original =
        sampleReservation(2L).toBuilder().status(ReservationStatus.DEADLINE_SYNC_REQUIRED).build();
    Reservation repaired = original.toBuilder().status(ReservationStatus.DEADLINE_REFUNDED).build();
    RepairReservationChainReadUseCase repairUseCase = mock(RepairReservationChainReadUseCase.class);
    GetTrainerReservationsService repairingSut =
        new GetTrainerReservationsService(
            loadReservationPort,
            loadClassSummaryPort,
            loadUserSummaryPort,
            null,
            repairUseCase,
            TEST_CLOCK);
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(original));
    given(repairUseCase.repairBatch(List.of(original))).willReturn(List.of(repaired));

    CursorSlice<ReservationSummaryResult> result =
        repairingSut.execute(
            new GetTrainerReservationsQuery(2L, ReservationStatus.DEADLINE_SYNC_REQUIRED));

    assertThat(result.items()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    then(repairUseCase).should().repairBatch(List.of(original));
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - repair 후 필터링으로 페이지가 비면 다음 raw page에서 보충한다")
  void execute_StatusFilter_SupplementsPageAfterRepairFiltering() {
    ReservationStatus status = ReservationStatus.DEADLINE_SYNC_REQUIRED;
    Reservation first =
        sampleReservation(2L).toBuilder()
            .id(30L)
            .reservationDate(LocalDate.of(2025, 6, 3))
            .status(status)
            .build();
    Reservation second =
        sampleReservation(2L).toBuilder()
            .id(20L)
            .reservationDate(LocalDate.of(2025, 6, 2))
            .status(status)
            .build();
    Reservation third =
        sampleReservation(2L).toBuilder()
            .id(10L)
            .reservationDate(LocalDate.of(2025, 6, 1))
            .status(status)
            .build();
    Reservation repairedFirst =
        first.toBuilder().status(ReservationStatus.DEADLINE_REFUNDED).build();
    RepairReservationChainReadUseCase repairUseCase = mock(RepairReservationChainReadUseCase.class);
    GetTrainerReservationsService repairingSut =
        new GetTrainerReservationsService(
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
            GetTrainerReservationsQuery.cursorScope(
                ReservationListStatusFilter.valueOf(status.name())));
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(first, second), List.of(third));
    given(repairUseCase.repairBatch(List.of(first, second)))
        .willReturn(List.of(repairedFirst, second));
    given(repairUseCase.repairBatch(List.of(third))).willReturn(List.of(third));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    CursorSlice<ReservationSummaryResult> result =
        repairingSut.execute(new GetTrainerReservationsQuery(2L, status, pageRequest));

    assertThat(result.items())
        .singleElement()
        .satisfies(item -> assertThat(item.reservationId()).isEqualTo(20L));
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isNotBlank();
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

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - size+1 probe 패턴: hasNext=true이면 nextCursor가 채워진다")
  void execute_HasNextTrue_NextCursorNotNull() {
    // given — port returns size+1 rows (probe indicates more pages exist)
    // Default convenience constructor uses size=20, so we need 21 rows.
    // We simulate this by building a list with 21 identical reservations.
    List<Reservation> probe = new java.util.ArrayList<>();
    for (int i = 0; i < 21; i++) {
      probe.add(sampleReservation(2L));
    }
    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any())).willReturn(probe);
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then — only 20 items returned (probe row trimmed); hasNext=true; cursor present
    assertThat(result.items()).hasSize(20);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isNotNull().isNotBlank();
  }

  @Test
  @DisplayName(
      "트레이너 수강 신청 목록 조회 - bookedPriceAmount만 있고 bookedClassTitle이 null인 partial snapshot은 어댑터 fallback 사용")
  void execute_PartialSnapshot_PriceOnlyFallsBackToAdapter() {
    // given — partial snapshot: priceAmount set but classTitle null
    Reservation reservation =
        sampleReservation(2L).toBuilder()
            .bookedPriceAmount(30000)
            // bookedClassTitle intentionally NOT set (null)
            .build();
    ClassSummary adapterSummary = new ClassSummary("어댑터 클래스 제목", 30000, "thumb/adapter.jpg");

    given(loadReservationPort.findByTrainerIdCursor(any(), any(), any()))
        .willReturn(List.of(reservation));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of(3L, adapterSummary));
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    CursorSlice<ReservationSummaryResult> result =
        sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then — partial snapshot triggers live fallback; classTitle must NOT be null
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).classTitle()).isEqualTo("어댑터 클래스 제목");
    assertThat(result.items().get(0).priceAmount()).isEqualTo(30000);
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - status가 다른 두 cursorScope 값은 서로 달라야 한다")
  void cursorScope_DifferentStatuses_ProduceDifferentScopes() {
    String allScope = GetTrainerReservationsQuery.cursorScope((ReservationListStatusFilter) null);
    String approvedScope =
        GetTrainerReservationsQuery.cursorScope(ReservationListStatusFilter.APPROVED);
    String pendingScope =
        GetTrainerReservationsQuery.cursorScope(ReservationListStatusFilter.PENDING);

    assertThat(allScope).isNotEqualTo(approvedScope);
    assertThat(allScope).isNotEqualTo(pendingScope);
    assertThat(approvedScope).isNotEqualTo(pendingScope);
    // user scope and trainer scope for same status must also differ
    assertThat(allScope).doesNotContain("user-reservations");
    assertThat(allScope).contains("trainer-reservations");
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
