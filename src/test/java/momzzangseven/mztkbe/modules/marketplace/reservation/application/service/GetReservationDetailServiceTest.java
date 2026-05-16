package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
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
class GetReservationDetailServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadClassSummaryPort loadClassSummaryPort;
  @Mock private LoadUserSummaryPort loadUserSummaryPort;

  @InjectMocks private GetReservationDetailService sut;

  private Reservation sampleReservation(Long userId, Long trainerId) {
    return Reservation.builder()
        .id(10L)
        .userId(userId)
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
  @DisplayName("예약 상세 조회 - 예약 소유 유저가 조회하면 성공")
  void execute_OwnerUser_ReturnsDetail() {
    // given
    Reservation reservation = sampleReservation(1L, 2L);
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    given(loadClassSummaryPort.findBySlotId(any())).willReturn(Optional.empty());
    given(loadUserSummaryPort.findById(any())).willReturn(Optional.empty());

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 1L));

    // then
    assertThat(result.reservationId()).isEqualTo(10L);
    assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
  }

  @Test
  @DisplayName("예약 상세 조회 - chain read repair 결과를 응답 매핑 전에 반영한다")
  void execute_AppliesChainReadRepairBeforeMapping() {
    Reservation original =
        sampleReservation(1L, 2L).toBuilder()
            .status(ReservationStatus.DEADLINE_SYNC_REQUIRED)
            .build();
    Reservation repaired = original.toBuilder().status(ReservationStatus.USER_CANCELLED).build();
    RepairReservationChainReadUseCase repairUseCase = mock(RepairReservationChainReadUseCase.class);
    GetReservationDetailService repairingSut =
        new GetReservationDetailService(
            loadReservationPort, loadClassSummaryPort, loadUserSummaryPort, null, repairUseCase);
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(original));
    given(repairUseCase.repairOne(original)).willReturn(repaired);
    given(loadClassSummaryPort.findBySlotId(any())).willReturn(Optional.empty());
    given(loadUserSummaryPort.findById(any())).willReturn(Optional.empty());

    GetReservationResult result = repairingSut.execute(new GetReservationQuery(10L, 1L));

    assertThat(result.status()).isEqualTo(ReservationStatus.USER_CANCELLED);
    then(repairUseCase).should().repairOne(original);
  }

  @Test
  @DisplayName("예약 상세 조회 - 담당 트레이너가 조회하면 성공")
  void execute_OwnerTrainer_ReturnsDetail() {
    // given
    Reservation reservation = sampleReservation(1L, 2L);
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    given(loadClassSummaryPort.findBySlotId(any())).willReturn(Optional.empty());
    given(loadUserSummaryPort.findById(any())).willReturn(Optional.empty());

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 2L));

    // then
    assertThat(result.trainerId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("예약 상세 조회 - 클래스/유저 정보가 모두 존재하면 enrichment 필드가 채워진다")
  void execute_AllSummariesPresent_EnrichmentFieldsPopulated() {
    // given
    Reservation reservation = sampleReservation(1L, 2L);
    ClassSummary classSummary = new ClassSummary("요가 기초", 50000, "thumbnail/key.jpg");
    UserSummary trainerSummary = new UserSummary(2L, "trainer-nick");
    UserSummary userSummary = new UserSummary(1L, "user-nick");

    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    given(loadClassSummaryPort.findBySlotId(3L)).willReturn(Optional.of(classSummary));
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.of(trainerSummary));
    given(loadUserSummaryPort.findById(1L)).willReturn(Optional.of(userSummary));

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 1L));

    // then — enrichment fields must be populated
    assertThat(result.classTitle()).isEqualTo("요가 기초");
    assertThat(result.priceAmount()).isEqualTo(50000);
    assertThat(result.thumbnailFinalObjectKey()).isEqualTo("thumbnail/key.jpg");
    assertThat(result.trainerNickname()).isEqualTo("trainer-nick");
    assertThat(result.userNickname()).isEqualTo("user-nick");
  }

  @Test
  @DisplayName("예약 상세 조회 - 비활성 클래스(classSummary 없음)이면 enrichment 필드는 null")
  void execute_InactiveClass_EnrichmentFieldsNull() {
    // given — classSummary is absent (e.g., class was deactivated)
    Reservation reservation = sampleReservation(1L, 2L);
    UserSummary trainerSummary = new UserSummary(2L, "trainer-nick");
    UserSummary userSummary = new UserSummary(1L, "user-nick");

    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    given(loadClassSummaryPort.findBySlotId(3L)).willReturn(Optional.empty());
    given(loadUserSummaryPort.findById(2L)).willReturn(Optional.of(trainerSummary));
    given(loadUserSummaryPort.findById(1L)).willReturn(Optional.of(userSummary));

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 1L));

    // then — class enrichment fields absent; user fields still populated
    assertThat(result.classTitle()).isNull();
    assertThat(result.priceAmount()).isNull();
    assertThat(result.thumbnailFinalObjectKey()).isNull();
    assertThat(result.trainerNickname()).isEqualTo("trainer-nick");
    assertThat(result.userNickname()).isEqualTo("user-nick");
  }

  @Test
  @DisplayName("예약 상세 조회 - 존재하지 않는 예약이면 ReservationNotFoundException")
  void execute_NotFound_ThrowsReservationNotFoundException() {
    // given
    given(loadReservationPort.findById(999L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> sut.execute(new GetReservationQuery(999L, 1L)))
        .isInstanceOf(ReservationNotFoundException.class);
  }

  @Test
  @DisplayName("예약 상세 조회 - 소유자도 트레이너도 아닌 요청자면 MarketplaceUnauthorizedAccessException")
  void execute_UnauthorizedRequester_ThrowsUnauthorizedException() {
    // given
    Reservation reservation = sampleReservation(1L, 2L);
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));

    // when & then
    assertThatThrownBy(() -> sut.execute(new GetReservationQuery(10L, 999L)))
        .isInstanceOf(MarketplaceUnauthorizedAccessException.class);
  }

  @Test
  @DisplayName("예약 상세 조회 - reservationId가 null이면 IllegalArgumentException")
  void execute_NullReservationId_ThrowsIllegalArgument() {
    assertThatThrownBy(() -> sut.execute(new GetReservationQuery(null, 1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("예약 상세 조회 - 두 스냅샷 필드가 모두 non-null이면 live fallback 없이 snapshot 값을 사용한다")
  void execute_FullSnapshot_UsesSnapshotValuesWithoutLiveLookup() {
    // given — both snapshot fields present
    Reservation reservation =
        sampleReservation(1L, 2L).toBuilder()
            .bookedPriceAmount(35000)
            .bookedClassTitle("요가 기초 (스냅샷)")
            .build();

    ClassSummary liveSummary = new ClassSummary("요가 심화 (최신)", 50000, "thumb/live.jpg");
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    // live summary still returned (for thumbnail); title/price must come from snapshot
    given(loadClassSummaryPort.findBySlotId(3L)).willReturn(Optional.of(liveSummary));
    given(loadUserSummaryPort.findById(any())).willReturn(Optional.empty());

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 1L));

    // then — snapshot wins for title and price; thumbnail comes from live summary
    assertThat(result.classTitle()).isEqualTo("요가 기초 (스냅샷)");
    assertThat(result.priceAmount()).isEqualTo(35000);
    assertThat(result.thumbnailFinalObjectKey()).isEqualTo("thumb/live.jpg");
  }

  @Test
  @DisplayName(
      "예약 상세 조회 - bookedPriceAmount만 있고 bookedClassTitle이 null인 partial snapshot은 live fallback을 탄다")
  void execute_PartialSnapshot_PriceOnlyFallsBackToLiveLookup() {
    // given — partial snapshot: priceAmount set, classTitle null (corrupt/partial write)
    Reservation reservation =
        sampleReservation(1L, 2L).toBuilder()
            .bookedPriceAmount(45000)
            // bookedClassTitle intentionally NOT set (null)
            .build();

    ClassSummary liveSummary = new ClassSummary("라이브 클래스 제목", 45000, "thumb/live.jpg");
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    given(loadClassSummaryPort.findBySlotId(3L)).willReturn(Optional.of(liveSummary));
    given(loadUserSummaryPort.findById(any())).willReturn(Optional.empty());

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 1L));

    // then — partial snapshot triggers live fallback; classTitle must NOT be null
    assertThat(result.classTitle()).isEqualTo("라이브 클래스 제목");
    assertThat(result.priceAmount()).isEqualTo(45000);
    assertThat(result.thumbnailFinalObjectKey()).isEqualTo("thumb/live.jpg");
  }

  @Test
  @DisplayName(
      "예약 상세 조회 - bookedClassTitle만 있고 bookedPriceAmount가 null인 partial snapshot도 live fallback을 탄다")
  void execute_PartialSnapshot_TitleOnlyFallsBackToLiveLookup() {
    // given — partial snapshot: classTitle set, priceAmount null
    Reservation reservation =
        sampleReservation(1L, 2L).toBuilder()
            .bookedClassTitle("스냅샷 제목만")
            // bookedPriceAmount intentionally NOT set (null)
            .build();

    ClassSummary liveSummary = new ClassSummary("라이브 클래스", 50000, "thumb/live.jpg");
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    given(loadClassSummaryPort.findBySlotId(3L)).willReturn(Optional.of(liveSummary));
    given(loadUserSummaryPort.findById(any())).willReturn(Optional.empty());

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 1L));

    // then — partial snapshot triggers live fallback; priceAmount must NOT be null
    assertThat(result.classTitle()).isEqualTo("라이브 클래스");
    assertThat(result.priceAmount()).isEqualTo(50000);
  }
}
