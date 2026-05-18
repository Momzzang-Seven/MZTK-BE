package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.global.time.TimeConfig;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter.MarketplaceReservationActionStatePersistenceAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter.MarketplaceReservationEscrowPersistenceAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter.ReservationCreateIdempotencyPersistenceAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter.ReservationExecutionCleanupProtectionPersistenceAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter.ReservationPersistenceAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationEscrowEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationSlotDateLockJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  ReservationPersistenceAdapter.class,
  MarketplaceReservationEscrowPersistenceAdapter.class,
  MarketplaceReservationActionStatePersistenceAdapter.class,
  ReservationCreateIdempotencyPersistenceAdapter.class,
  ReservationExecutionCleanupProtectionPersistenceAdapter.class,
  TimeConfig.class
})
class ReservationPersistenceAdapterTest {

  @Autowired private EntityManager entityManager;
  @Autowired private ReservationPersistenceAdapter reservationAdapter;
  @Autowired private MarketplaceReservationEscrowPersistenceAdapter escrowAdapter;
  @Autowired private MarketplaceReservationActionStatePersistenceAdapter actionStateAdapter;
  @Autowired private ReservationCreateIdempotencyPersistenceAdapter idempotencyAdapter;

  @Autowired
  private ReservationExecutionCleanupProtectionPersistenceAdapter cleanupProtectionAdapter;

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
    assertThat(reservationAdapter.countActiveReservationsBySlotIdAndDateWithLock(slotId, date))
        .isEqualTo(3);
    assertThat(reservationAdapter.countActiveReservationsBySlotId(slotId)).isEqualTo(3);
    assertThat(reservationAdapter.countActiveReservationsBySlotIds(List.of(slotId)))
        .containsEntry(slotId, 3);
    assertThat(
            reservationAdapter.countActiveReservationsBySlotIdAndDateRange(
                slotId, date.minusDays(1), date.plusDays(1)))
        .containsEntry(date, 3);
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
  @DisplayName("escrow priceBaseUnits persists uint256 max without precision loss")
  void escrowPriceBaseUnitsRoundTripUint256Max() {
    BigInteger maxUint256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    MarketplaceReservationEscrow saved =
        escrowAdapter.save(
            MarketplaceReservationEscrow.builder()
                .reservationId(100L)
                .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
                .escrowStatus(ReservationEscrowStatus.NONE)
                .orderKey("0x" + "1".repeat(64))
                .priceBaseUnits(maxUint256)
                .build());

    entityManager.flush();
    entityManager.clear();

    MarketplaceReservationEscrow loaded =
        escrowAdapter.findByReservationId(saved.getReservationId()).orElseThrow();

    assertThat(loaded.getPriceBaseUnits()).isEqualTo(maxUint256);
  }

  @Test
  @DisplayName("escrow priceBaseUnits rejects values outside Solidity uint256 range")
  void escrowPriceBaseUnitsRejectsOutOfRangeValues() {
    BigInteger overflow = BigInteger.ONE.shiftLeft(256);

    assertThatThrownBy(
            () ->
                escrowAdapter.save(
                    MarketplaceReservationEscrow.builder()
                        .reservationId(101L)
                        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
                        .escrowStatus(ReservationEscrowStatus.NONE)
                        .orderKey("0x" + "2".repeat(64))
                        .priceBaseUnits(overflow)
                        .build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uint256");

    assertThatThrownBy(
            () ->
                escrowAdapter.save(
                    MarketplaceReservationEscrow.builder()
                        .reservationId(102L)
                        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
                        .escrowStatus(ReservationEscrowStatus.NONE)
                        .orderKey("0x" + "3".repeat(64))
                        .priceBaseUnits(BigInteger.valueOf(-1L))
                        .build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uint256");
  }

  @Test
  @DisplayName("create idempotency lifecycle is persisted and loaded with lock")
  void createIdempotencyRoundTrip() {
    ReservationCreateIdempotency saved =
        idempotencyAdapter.save(
            ReservationCreateIdempotency.preparing(
                    1L, "key-hash", "payload-hash", LocalDateTime.of(2026, 5, 16, 10, 0))
                .markFailed("{\"ok\":false}"));

    entityManager.flush();
    entityManager.clear();

    ReservationCreateIdempotency loaded =
        idempotencyAdapter.findByBuyerIdAndKeyHashWithLock(1L, "key-hash").orElseThrow();

    assertThat(loaded.getId()).isEqualTo(saved.getId());
    assertThat(loaded.getStatus()).isEqualTo(ReservationCreateIdempotencyStatus.FAILED);
    assertThat(loaded.getResponseSnapshotJson()).isEqualTo("{\"ok\":false}");
  }

  @Test
  @DisplayName(
      "reservePreparing inserts once and duplicate calls return the existing idempotency row")
  void reservePreparingInsertsOnceAndReturnsExistingOnDuplicate() {
    LocalDateTime firstExpiresAt = LocalDateTime.of(2026, 5, 16, 10, 0);

    var first = idempotencyAdapter.reservePreparing(1L, "reserve-key", "payload-a", firstExpiresAt);
    var duplicate =
        idempotencyAdapter.reservePreparing(
            1L, "reserve-key", "payload-b", firstExpiresAt.plusMinutes(5));

    assertThat(first.created()).isTrue();
    assertThat(duplicate.created()).isFalse();
    assertThat(duplicate.idempotency().getId()).isEqualTo(first.idempotency().getId());
    assertThat(duplicate.idempotency().getPayloadHash()).isEqualTo("payload-a");
    assertThat(duplicate.idempotency().getStatus())
        .isEqualTo(ReservationCreateIdempotencyStatus.PREPARING);
    assertThat(duplicate.idempotency().getExpiresAt()).isEqualTo(firstExpiresAt);
  }

  @Test
  @DisplayName("create idempotency action state replacement requires the expected current state")
  void replaceActionStateIfCurrentRequiresExpectedActionState() {
    ReservationCreateIdempotency saved =
        idempotencyAdapter.save(
            ReservationCreateIdempotency.preparing(
                    1L, "replace-key", "payload-hash", LocalDateTime.of(2026, 5, 16, 10, 0))
                .attachReservationGraph(10L, 20L, 30L)
                .markBound("{\"ok\":true}"));

    assertThat(idempotencyAdapter.replaceActionStateIfCurrent(saved.getId(), 999L, 40L)).isEmpty();

    ReservationCreateIdempotency replaced =
        idempotencyAdapter.replaceActionStateIfCurrent(saved.getId(), 30L, 40L).orElseThrow();

    assertThat(replaced.getActionStateId()).isEqualTo(40L);
    assertThat(idempotencyAdapter.replaceActionStateIfCurrent(saved.getId(), 30L, 50L)).isEmpty();
  }

  @Test
  @DisplayName("cleanup protection queries protect reservation and unbound pending refs")
  void cleanupProtectionQueriesReturnProtectedPublicIds() {
    Long slotId = saveSlot();
    Reservation current =
        reservationAdapter.save(
            Reservation.builder()
                .userId(1L)
                .trainerId(2L)
                .slotId(slotId)
                .reservationDate(LocalDate.of(2026, 5, 23))
                .reservationTime(LocalTime.of(10, 0))
                .durationMinutes(60)
                .status(ReservationStatus.PENDING)
                .escrowStatus(ReservationEscrowStatus.LOCKED)
                .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
                .currentExecutionIntentPublicId("intent-current")
                .build());
    Reservation unbound =
        reservationAdapter.save(
            Reservation.builder()
                .userId(3L)
                .trainerId(2L)
                .slotId(slotId)
                .reservationDate(LocalDate.of(2026, 5, 24))
                .reservationTime(LocalTime.of(10, 0))
                .durationMinutes(60)
                .status(ReservationStatus.CONFIRM_PENDING)
                .escrowStatus(ReservationEscrowStatus.CONFIRM_PENDING)
                .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
                .pendingAction(ReservationEscrowAction.BUYER_CONFIRM)
                .build());
    entityManager.flush();
    entityManager.clear();

    List<String> protectedPublicIds =
        cleanupProtectionAdapter.findProtectedExecutionIntentPublicIds(
            List.of(
                new ReservationExecutionCleanupProtectionQuery(
                    "intent-current",
                    String.valueOf(current.getId()),
                    "MARKETPLACE_CLASS_CANCEL",
                    marketplacePayload(
                        current.getId(), 10L, 20L, "attempt-current", "MARKETPLACE_CLASS_CANCEL")),
                new ReservationExecutionCleanupProtectionQuery(
                    "intent-unbound",
                    String.valueOf(unbound.getId()),
                    "MARKETPLACE_CLASS_CONFIRM",
                    marketplacePayload(
                        unbound.getId(), 11L, 21L, "attempt-unbound", "MARKETPLACE_CLASS_CONFIRM")),
                new ReservationExecutionCleanupProtectionQuery(
                    "intent-free",
                    "999999",
                    "MARKETPLACE_CLASS_CONFIRM",
                    marketplacePayload(
                        999999L, 12L, 22L, "attempt-free", "MARKETPLACE_CLASS_CONFIRM"))));

    assertThat(protectedPublicIds).containsExactlyInAnyOrder("intent-current", "intent-unbound");
  }

  @Test
  @DisplayName(
      "action state bind only succeeds for matching PREPARING attempt token and empty intent")
  void actionStateBindRequiresPreparingTokenAndUnboundIntent() {
    MarketplaceReservationActionState preparing =
        actionStateAdapter.save(
            actionState(
                10L, 20L, 1, "attempt-token", ReservationActionStateStatus.PREPARING, null));

    assertThat(
            actionStateAdapter.bindExecutionIntent(
                preparing.getId(), "wrong-token", "intent-wrong-token"))
        .isEmpty();

    MarketplaceReservationActionState bound =
        actionStateAdapter
            .bindExecutionIntent(preparing.getId(), "attempt-token", "intent-bound")
            .orElseThrow();

    assertThat(bound.getExecutionIntentPublicId()).isEqualTo("intent-bound");
    assertThat(bound.getStatus()).isEqualTo(ReservationActionStateStatus.INTENT_BOUND);
    assertThat(
            actionStateAdapter.bindExecutionIntent(preparing.getId(), "attempt-token", "intent-2"))
        .isEmpty();

    MarketplaceReservationActionState failed =
        actionStateAdapter.save(
            actionState(
                11L,
                21L,
                1,
                "failed-token",
                ReservationActionStateStatus.PREPARATION_FAILED,
                null));
    assertThat(actionStateAdapter.bindExecutionIntent(failed.getId(), "failed-token", "intent-3"))
        .isEmpty();
  }

  private String marketplacePayload(
      Long reservationId,
      Long escrowId,
      Long actionStateId,
      String attemptToken,
      String actionType) {
    return """
        {
          "payloadVersion": 1,
          "reservationId": %d,
          "escrowId": %d,
          "actionStateId": %d,
          "pendingAttemptToken": "%s",
          "actionType": "%s"
        }
        """
        .formatted(reservationId, escrowId, actionStateId, attemptToken, actionType);
  }

  private MarketplaceReservationActionState actionState(
      Long reservationId,
      Long escrowId,
      int attemptNo,
      String attemptToken,
      ReservationActionStateStatus status,
      String executionIntentPublicId) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 16, 10, 0).plusMinutes(attemptNo);
    return MarketplaceReservationActionState.builder()
        .reservationId(reservationId)
        .escrowId(escrowId)
        .actionType(ReservationEscrowAction.BUYER_CANCEL)
        .actorType(ReservationEscrowActorType.BUYER)
        .actorUserId(1L)
        .attemptNo(attemptNo)
        .attemptToken(attemptToken)
        .executionIntentPublicId(executionIntentPublicId)
        .rootIdempotencyKey("root-" + reservationId)
        .payloadHash("0x" + "a".repeat(64))
        .status(status)
        .expectedReservationStatus(ReservationStatus.PENDING)
        .expectedEscrowStatus(ReservationEscrowStatus.LOCKED)
        .preparationExpiresAt(now.plusMinutes(10))
        .createdAt(now)
        .updatedAt(now)
        .build();
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
    Reservation saved =
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
    if (flow.isUserEip7702() && holdExpiresAt != null) {
      entityManager.persist(
          MarketplaceReservationEscrowEntity.builder()
              .reservationId(saved.getId())
              .escrowFlow(flow.name())
              .escrowStatus(ReservationEscrowStatus.NONE.name())
              .holdExpiresAt(holdExpiresAt)
              .build());
    }
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
