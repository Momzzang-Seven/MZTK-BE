package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.MarketplaceClassEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository.ClassSlotJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository.MarketplaceClassJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationActionStateEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationEscrowEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class MarketplaceWeb3AutoSettleCandidateQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 29, 12, 0);
  private static final LocalDateTime SETTLE_CUTOFF = NOW.minusHours(24);

  @Autowired private ReservationJpaRepository reservationJpaRepository;
  @Autowired private MarketplaceReservationEscrowJpaRepository escrowJpaRepository;
  @Autowired private MarketplaceReservationActionStateJpaRepository actionStateJpaRepository;
  @Autowired private MarketplaceClassJpaRepository marketplaceClassJpaRepository;
  @Autowired private ClassSlotJpaRepository classSlotJpaRepository;

  private long sequence = 1L;

  @Test
  @DisplayName("query는 APPROVED / LOCKED / USER_EIP7702 후보만 반환한다")
  void findMarketplaceWeb3AutoSettleCandidates_selectsEligibleRows() {
    SeededReservation eligible =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 28),
            LocalTime.of(11, 0),
            null,
            null,
            null);
    seedReservation(
        "APPROVED",
        "LEGACY_DISPATCH",
        "LOCKED",
        "LEGACY_DISPATCH",
        "LOCKED",
        LocalDate.of(2026, 5, 28),
        LocalTime.of(10, 0),
        null,
        null,
        null);
    seedReservation(
        "APPROVED",
        "USER_EIP7702",
        "LOCKED",
        "USER_EIP7702",
        "LOCKED",
        LocalDate.of(2026, 5, 29),
        LocalTime.of(10, 0),
        null,
        null,
        null);
    seedReservation(
        "APPROVED",
        "USER_EIP7702",
        "LOCKED",
        "USER_EIP7702",
        "LOCKED",
        LocalDate.of(2026, 5, 28),
        LocalTime.of(10, 30),
        NOW,
        null,
        null);

    assertThat(findCandidateIds(null, null, null, 20)).containsExactly(eligible.reservationId());
  }

  @Test
  @DisplayName("query는 active action과 reservation current intent를 제외한다")
  void findMarketplaceWeb3AutoSettleCandidates_excludesActiveRows() {
    SeededReservation eligible =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(10, 0),
            null,
            null,
            null);
    SeededReservation activeAction =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(10, 5),
            null,
            null,
            null);
    saveActionState(activeAction, "ADMIN_SETTLE", "PREPARING", 1, null);
    seedReservation(
        "APPROVED",
        "USER_EIP7702",
        "LOCKED",
        "USER_EIP7702",
        "LOCKED",
        LocalDate.of(2026, 5, 27),
        LocalTime.of(10, 10),
        null,
        "intent-current",
        null);
    assertThat(findCandidateIds(null, null, null, 20)).containsExactly(eligible.reservationId());
  }

  @Test
  @DisplayName(
      "query는 latest scheduler non-retryable admin settle failure를 제외하고 manual/retryable/superseded failure는 허용한다")
  void findMarketplaceWeb3AutoSettleCandidates_handlesPreparationFailureStatus() {
    SeededReservation latestNonRetryableScheduler =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(13, 0),
            null,
            null,
            null);
    saveActionState(
        latestNonRetryableScheduler, "ADMIN_SETTLE", "PREPARATION_FAILED", 1, false, "SCHEDULER");
    SeededReservation latestNonRetryableManual =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(13, 3),
            null,
            null,
            null);
    saveActionState(
        latestNonRetryableManual, "ADMIN_SETTLE", "PREPARATION_FAILED", 1, false, "MANUAL_ADMIN");
    SeededReservation nullRetryable =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(13, 5),
            null,
            null,
            null);
    saveActionState(nullRetryable, "ADMIN_SETTLE", "PREPARATION_FAILED", 1, null, "SCHEDULER");
    SeededReservation retryableLatest =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(13, 10),
            null,
            null,
            null);
    saveActionState(retryableLatest, "ADMIN_SETTLE", "PREPARATION_FAILED", 1, true, "SCHEDULER");
    SeededReservation supersededFailure =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(13, 15),
            null,
            null,
            null);
    saveActionState(supersededFailure, "ADMIN_SETTLE", "PREPARATION_FAILED", 1, false, "SCHEDULER");
    saveActionState(supersededFailure, "ADMIN_SETTLE", "PREPARATION_FAILED", 2, true, "SCHEDULER");
    SeededReservation schedulerFailureFollowedByManualFailure =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(13, 20),
            null,
            null,
            null);
    saveActionState(
        schedulerFailureFollowedByManualFailure,
        "ADMIN_SETTLE",
        "PREPARATION_FAILED",
        1,
        false,
        "SCHEDULER");
    saveActionState(
        schedulerFailureFollowedByManualFailure,
        "ADMIN_SETTLE",
        "PREPARATION_FAILED",
        2,
        false,
        "MANUAL_ADMIN");

    assertThat(findCandidateIds(null, null, null, 20))
        .containsExactly(
            latestNonRetryableManual.reservationId(),
            retryableLatest.reservationId(),
            supersededFailure.reservationId());
  }

  @Test
  @DisplayName("query는 order_key mismatch를 제외하고 keyset cursor pagination을 지원한다")
  void findMarketplaceWeb3AutoSettleCandidates_supportsCursorPagination() {
    seedReservation(
        "APPROVED",
        "USER_EIP7702",
        "LOCKED",
        "USER_EIP7702",
        "LOCKED",
        LocalDate.of(2026, 5, 27),
        LocalTime.of(8, 55),
        null,
        null,
        randomOrderKey());
    SeededReservation first =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(9, 0),
            null,
            null,
            null);
    SeededReservation second =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(9, 5),
            null,
            null,
            null);
    SeededReservation third =
        seedReservation(
            "APPROVED",
            "USER_EIP7702",
            "LOCKED",
            "USER_EIP7702",
            "LOCKED",
            LocalDate.of(2026, 5, 27),
            LocalTime.of(9, 10),
            null,
            null,
            null);

    List<ReservationJpaRepository.MarketplaceWeb3AutoSettleCandidateProjection> firstPage =
        findCandidates(null, null, null, 2);
    List<ReservationJpaRepository.MarketplaceWeb3AutoSettleCandidateProjection> secondPage =
        findCandidates(
            firstPage.get(1).getReservationDate(),
            firstPage.get(1).getReservationTime(),
            firstPage.get(1).getReservationId(),
            2);

    assertThat(firstPage)
        .extracting(
            ReservationJpaRepository.MarketplaceWeb3AutoSettleCandidateProjection::getReservationId)
        .containsExactly(first.reservationId(), second.reservationId());
    assertThat(secondPage)
        .extracting(
            ReservationJpaRepository.MarketplaceWeb3AutoSettleCandidateProjection::getReservationId)
        .containsExactly(third.reservationId());
  }

  private List<Long> findCandidateIds(
      LocalDate cursorDate, LocalTime cursorTime, Long cursorId, int scanSize) {
    return findCandidates(cursorDate, cursorTime, cursorId, scanSize).stream()
        .map(
            ReservationJpaRepository.MarketplaceWeb3AutoSettleCandidateProjection::getReservationId)
        .toList();
  }

  private List<ReservationJpaRepository.MarketplaceWeb3AutoSettleCandidateProjection>
      findCandidates(LocalDate cursorDate, LocalTime cursorTime, Long cursorId, int scanSize) {
    return reservationJpaRepository.findMarketplaceWeb3AutoSettleCandidates(
        NOW,
        SETTLE_CUTOFF.toLocalDate(),
        SETTLE_CUTOFF.toLocalTime(),
        cursorDate,
        cursorTime,
        cursorId,
        scanSize);
  }

  private SeededReservation seedReservation(
      String reservationStatus,
      String reservationEscrowFlow,
      String reservationEscrowStatus,
      String escrowFlow,
      String escrowStatus,
      LocalDate reservationDate,
      LocalTime reservationTime,
      LocalDateTime contractDeadlineAt,
      String currentExecutionIntentPublicId,
      String escrowOrderKeyOverride) {
    long suffix = sequence++;
    String orderKey = randomOrderKey();
    MarketplaceClassEntity marketplaceClass =
        marketplaceClassJpaRepository.saveAndFlush(
            MarketplaceClassEntity.builder()
                .trainerId(2_000L + suffix)
                .title("auto-settle-class-" + suffix)
                .category(ClassCategory.PT)
                .description("query fixture")
                .priceAmount(100)
                .durationMinutes(60)
                .active(true)
                .build());
    ClassSlotEntity slot =
        classSlotJpaRepository.saveAndFlush(
            ClassSlotEntity.builder()
                .classId(marketplaceClass.getId())
                .daysOfWeek(List.of(DayOfWeek.from(reservationDate)))
                .startTime(reservationTime)
                .capacity(1)
                .active(true)
                .build());
    ReservationEntity reservation =
        reservationJpaRepository.saveAndFlush(
            ReservationEntity.builder()
                .userId(100L + suffix)
                .trainerId(200L + suffix)
                .slotId(slot.getId())
                .reservationDate(reservationDate)
                .reservationTime(reservationTime)
                .durationMinutes(60)
                .status(reservationStatus)
                .escrowStatus(reservationEscrowStatus)
                .escrowFlow(reservationEscrowFlow)
                .orderId("order-id-" + suffix)
                .orderKey(orderKey)
                .currentExecutionIntentPublicId(currentExecutionIntentPublicId)
                .buyerWalletAddress(wallet('1'))
                .trainerWalletAddress(wallet('2'))
                .tokenAddress(wallet('3'))
                .priceBaseUnits("100")
                .contractDeadlineAt(contractDeadlineAt)
                .contractDeadlineEpochSeconds(
                    contractDeadlineAt == null
                        ? null
                        : contractDeadlineAt.atZone(java.time.ZoneOffset.UTC).toEpochSecond())
                .bookedPriceAmount(100)
                .build());
    MarketplaceReservationEscrowEntity escrow =
        escrowJpaRepository.saveAndFlush(
            MarketplaceReservationEscrowEntity.builder()
                .reservationId(reservation.getId())
                .escrowFlow(escrowFlow)
                .escrowStatus(escrowStatus)
                .orderKey(escrowOrderKeyOverride == null ? orderKey : escrowOrderKeyOverride)
                .buyerWalletAddress(wallet('1'))
                .trainerWalletAddress(wallet('2'))
                .tokenAddress(wallet('3'))
                .priceBaseUnits(BigDecimal.valueOf(100))
                .contractDeadlineAt(contractDeadlineAt)
                .contractDeadlineEpochSeconds(
                    contractDeadlineAt == null
                        ? null
                        : contractDeadlineAt.atZone(java.time.ZoneOffset.UTC).toEpochSecond())
                .build());
    return new SeededReservation(reservation.getId(), escrow.getId(), orderKey);
  }

  private void saveActionState(
      SeededReservation reservation,
      String actionType,
      String status,
      int attemptNo,
      Boolean retryable) {
    saveActionState(reservation, actionType, status, attemptNo, retryable, "SCHEDULER");
  }

  private void saveActionState(
      SeededReservation reservation,
      String actionType,
      String status,
      int attemptNo,
      Boolean retryable,
      String requestSource) {
    actionStateJpaRepository.saveAndFlush(
        MarketplaceReservationActionStateEntity.builder()
            .reservationId(reservation.reservationId())
            .escrowId(reservation.escrowId())
            .actionType(actionType)
            .actorType("MANUAL_ADMIN".equals(requestSource) ? "ADMIN" : "SYSTEM")
            .requestSource(requestSource)
            .attemptNo(attemptNo)
            .attemptToken("attempt-" + reservation.reservationId() + "-" + attemptNo)
            .status(status)
            .retryable(retryable)
            .reasonCode("BUYER_CONFIRMATION_TIMEOUT")
            .build());
  }

  private String randomOrderKey() {
    return "0x" + String.format("%064x", sequence * 111);
  }

  private String wallet(char ch) {
    return "0x" + String.valueOf(ch).repeat(40);
  }

  private record SeededReservation(Long reservationId, Long escrowId, String orderKey) {}
}
