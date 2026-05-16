package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter.ReservationCreateIdempotencyPersistenceAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter.ReservationPersistenceAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationSlotDateLockJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({ReservationPersistenceAdapter.class, ReservationCreateIdempotencyPersistenceAdapter.class})
class ReservationPersistenceAdapterTest {

  @Autowired private EntityManager entityManager;
  @Autowired private ReservationPersistenceAdapter reservationAdapter;
  @Autowired private ReservationCreateIdempotencyPersistenceAdapter idempotencyAdapter;
  @Autowired private ReservationSlotDateLockJpaRepository slotDateLockRepository;

  @Test
  @DisplayName("new escrow fields round-trip through ReservationEntity mapping")
  void escrowFieldsRoundTrip() {
    Long slotId = saveSlot();
    Reservation saved =
        reservationAdapter.save(
            Reservation.builder()
                .userId(1L)
                .trainerId(2L)
                .slotId(slotId)
                .reservationDate(LocalDate.of(2026, 5, 20))
                .reservationTime(LocalTime.of(10, 0))
                .durationMinutes(60)
                .status(ReservationStatus.PURCHASE_PENDING)
                .escrowStatus(ReservationEscrowStatus.PURCHASE_PENDING)
                .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
                .orderId("11111111-1111-1111-1111-111111111111")
                .orderKey("0x" + "1".repeat(64))
                .currentExecutionIntentPublicId("22222222-2222-2222-2222-222222222222")
                .buyerWalletAddress("0x" + "a".repeat(40))
                .trainerWalletAddress("0x" + "b".repeat(40))
                .tokenAddress("0x" + "c".repeat(40))
                .priceBaseUnits("1000000000000000000")
                .holdExpiresAt(LocalDateTime.of(2026, 5, 16, 10, 0))
                .contractDeadlineEpochSeconds(1_800_000_000L)
                .contractDeadlineAt(LocalDateTime.of(2027, 1, 15, 8, 0))
                .createIdempotencyKeyHash("key-hash")
                .createPayloadHash("payload-hash")
                .build());

    entityManager.flush();
    entityManager.clear();

    Reservation loaded = reservationAdapter.findById(saved.getId()).orElseThrow();

    assertThat(loaded.getEscrowFlow()).isEqualTo(ReservationEscrowFlow.USER_EIP7702);
    assertThat(loaded.getEscrowStatus()).isEqualTo(ReservationEscrowStatus.PURCHASE_PENDING);
    assertThat(loaded.getOrderKey()).isEqualTo("0x" + "1".repeat(64));
    assertThat(loaded.getCurrentExecutionIntentPublicId())
        .isEqualTo("22222222-2222-2222-2222-222222222222");
    assertThat(loaded.getContractDeadlineEpochSeconds()).isEqualTo(1_800_000_000L);
    assertThat(loaded.getContractDeadlineAt()).isEqualTo(LocalDateTime.of(2027, 1, 15, 8, 0));
  }

  @Test
  @DisplayName("capacity count includes purchase pending and excludes released terminal holds")
  void capacityCountIncludesUserPurchaseHolds() {
    Long slotId = saveSlot();
    LocalDate date = LocalDate.of(2026, 5, 21);
    saveReservation(slotId, date, ReservationStatus.PENDING);
    saveReservation(slotId, date, ReservationStatus.PURCHASE_PREPARING);
    saveReservation(
        slotId,
        date,
        ReservationStatus.PURCHASE_PREPARING,
        ReservationEscrowFlow.USER_EIP7702,
        LocalDateTime.now().minusMinutes(1));
    saveReservation(slotId, date, ReservationStatus.PURCHASE_PENDING);
    saveReservation(slotId, date, ReservationStatus.HOLD_EXPIRED);
    saveReservation(slotId, date, ReservationStatus.PAYMENT_FAILED);

    int count = reservationAdapter.countActiveReservationsBySlotIdAndDate(slotId, date);

    assertThat(count).isEqualTo(3);
  }

  @Test
  @DisplayName("slot/date capacity lock can be created idempotently before active count")
  void lockSlotDateCapacityKeyCreatesKey() {
    Long slotId = saveSlot();
    LocalDate date = LocalDate.of(2026, 5, 22);

    reservationAdapter.lockSlotDateCapacityKey(slotId, date);
    reservationAdapter.lockSlotDateCapacityKey(slotId, date);

    assertThat(slotDateLockRepository.findAll())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getSlotId()).isEqualTo(slotId);
              assertThat(row.getReservationDate()).isEqualTo(date);
            });
  }

  @Test
  @DisplayName("create idempotency lifecycle is persisted and loaded with lock")
  void createIdempotencyRoundTrip() {
    ReservationCreateIdempotency saved =
        idempotencyAdapter.save(
            ReservationCreateIdempotency.preparing(
                    1L, "key-hash", "payload-hash", LocalDateTime.of(2026, 5, 16, 10, 0))
                .markBound(10L, "33333333-3333-3333-3333-333333333333", "{\"ok\":true}"));

    entityManager.flush();
    entityManager.clear();

    ReservationCreateIdempotency loaded =
        idempotencyAdapter.findByBuyerIdAndKeyHashWithLock(1L, "key-hash").orElseThrow();

    assertThat(loaded.getId()).isEqualTo(saved.getId());
    assertThat(loaded.getStatus()).isEqualTo(ReservationCreateIdempotencyStatus.BOUND);
    assertThat(loaded.getReservationId()).isEqualTo(10L);
    assertThat(loaded.getCurrentExecutionIntentPublicId())
        .isEqualTo("33333333-3333-3333-3333-333333333333");
  }

  @Test
  @DisplayName("legacy scheduler candidate queries exclude USER_EIP7702 rows")
  void schedulerCandidateQueriesExcludeUserEip7702Rows() {
    Long slotId = saveSlot();
    LocalDate oldDate = LocalDate.of(2026, 5, 1);
    saveReservation(
        slotId, oldDate, ReservationStatus.PENDING, ReservationEscrowFlow.LEGACY_DISPATCH);
    saveReservation(slotId, oldDate, ReservationStatus.PENDING, ReservationEscrowFlow.USER_EIP7702);
    saveReservation(
        slotId, oldDate, ReservationStatus.APPROVED, ReservationEscrowFlow.LEGACY_DISPATCH);
    saveReservation(
        slotId, oldDate, ReservationStatus.APPROVED, ReservationEscrowFlow.USER_EIP7702);

    assertThat(
            reservationAdapter.findPendingForAutoCancel(
                LocalDateTime.of(2026, 5, 15, 0, 0), LocalDateTime.of(2026, 5, 20, 0, 0), 10))
        .hasSize(1)
        .allMatch(row -> row.getEffectiveEscrowFlow().isLegacyDispatch());

    assertThat(
            reservationAdapter.findApprovedForAutoSettle(LocalDateTime.of(2026, 5, 20, 0, 0), 10))
        .hasSize(1)
        .allMatch(row -> row.getEffectiveEscrowFlow().isLegacyDispatch());
  }

  private void saveReservation(Long slotId, LocalDate date, ReservationStatus status) {
    saveReservation(slotId, date, status, ReservationEscrowFlow.LEGACY_DISPATCH);
  }

  private void saveReservation(
      Long slotId, LocalDate date, ReservationStatus status, ReservationEscrowFlow flow) {
    saveReservation(slotId, date, status, flow, null);
  }

  private void saveReservation(
      Long slotId,
      LocalDate date,
      ReservationStatus status,
      ReservationEscrowFlow flow,
      LocalDateTime holdExpiresAt) {
    reservationAdapter.save(
        Reservation.builder()
            .userId(System.nanoTime())
            .trainerId(2L)
            .slotId(slotId)
            .reservationDate(date)
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(status)
            .escrowStatus(ReservationEscrowStatus.NONE)
            .escrowFlow(flow)
            .holdExpiresAt(holdExpiresAt)
            .build());
  }

  private Long saveSlot() {
    ClassSlotEntity slot =
        ClassSlotEntity.builder()
            .classId(1L)
            .startTime(LocalTime.of(10, 0))
            .capacity(5)
            .active(true)
            .build();
    entityManager.persist(slot);
    entityManager.flush();
    return slot.getId();
  }
}
