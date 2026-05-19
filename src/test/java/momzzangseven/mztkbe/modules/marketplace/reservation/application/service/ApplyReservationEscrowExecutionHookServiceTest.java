package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionConfirmedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplyReservationEscrowExecutionHookServiceTest {

  private static final long CONTRACT_DEADLINE_EPOCH_SECONDS =
      Instant.parse("2026-05-22T11:00:00Z").getEpochSecond();

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private SaveReservationActionStatePort saveReservationActionStatePort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private SaveReservationEscrowPort saveReservationEscrowPort;
  @Mock private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;

  private ApplyReservationEscrowExecutionHookService service;

  @BeforeEach
  void setUp() {
    service =
        new ApplyReservationEscrowExecutionHookService(
            loadReservationPort,
            saveReservationPort,
            Clock.fixed(Instant.parse("2026-05-16T00:00:00Z"), ZoneOffset.UTC),
            null,
            loadReservationEscrowOrderPort,
            null,
            null);
    service.setTransactionPort(ReservationTestTransactionPort.direct());
    service.setActionStatePorts(loadReservationActionStatePort, saveReservationActionStatePort);
    service.setEscrowProjectionPorts(loadReservationEscrowPort, saveReservationEscrowPort);
  }

  @ParameterizedTest
  @MethodSource("purchaseConfirmedChainStates")
  void confirmedPurchaseHook_syncsOnChainOrderState(
      int chainState,
      ReservationStatus expectedStatus,
      ReservationEscrowStatus expectedEscrowStatus) {
    Reservation reservation = pendingPurchaseReservation();
    given(loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey()))
        .willReturn(orderView(chainState));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(purchaseConfirmedCommand(reservation));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(expectedStatus);
    assertThat(reservationCaptor.getValue().getEffectiveEscrowStatus())
        .isEqualTo(expectedEscrowStatus);
  }

  @Test
  void confirmedPurchaseHook_marksDeadlineSyncRequiredWhenOrderIsAbsent() {
    Reservation reservation = pendingPurchaseReservation();
    given(loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey()))
        .willReturn(
            new ReservationEscrowOrderView(
                reservation.getOrderKey(),
                "50000",
                "0x3333333333333333333333333333333333333333",
                1_800L,
                ReservationEscrowOrderView.STATE_ABSENT,
                "0x0000000000000000000000000000000000000000",
                "0x2222222222222222222222222222222222222222"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(purchaseConfirmedCommand(reservation));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus())
        .isEqualTo(ReservationStatus.DEADLINE_SYNC_REQUIRED);
    assertThat(reservationCaptor.getValue().getEffectiveEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.DEADLINE_SYNC_REQUIRED);
  }

  @Test
  void confirmedHook_marksMatchingActionStateConfirmed() {
    Reservation reservation = pendingCancelReservation();
    MarketplaceReservationActionState actionState = activeActionState();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(
        new ReservationEscrowExecutionConfirmedCommand(
            "intent-1",
            "0xtx",
            "MARKETPLACE_CLASS_CANCEL",
            "BUYER",
            reservation.getId(),
            reservation.getOrderKey(),
            null,
            null,
            reservation.sessionEndAt(),
            "attempt-1",
            20L));

    ArgumentCaptor<MarketplaceReservationActionState> captor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationActionStateStatus.CONFIRMED);
    assertThat(captor.getValue().getRetryable()).isFalse();
    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.REFUNDED);
    assertThat(escrowCaptor.getValue().getLastTxHash()).isEqualTo("0xtx");
  }

  @Test
  void terminatedHook_marksMatchingActionStateRetryableForRetryableTerminal() {
    Reservation reservation = pendingCancelReservation();
    MarketplaceReservationActionState actionState = activeActionState();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-1",
            "MARKETPLACE_CLASS_CANCEL",
            "BUYER",
            reservation.getId(),
            "attempt-1",
            20L,
            "FAILED_ONCHAIN",
            "revert"));

    ArgumentCaptor<MarketplaceReservationActionState> captor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationActionStateStatus.TERMINATED);
    assertThat(captor.getValue().getRetryable()).isTrue();
    assertThat(captor.getValue().getErrorCode()).isEqualTo("FAILED_ONCHAIN");
    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(escrowCaptor.getValue().getLastFailureCode()).isEqualTo("FAILED_ONCHAIN");
  }

  @Test
  void terminatedHook_ignoresUnboundPurchaseIntent() {
    Reservation reservation =
        Reservation.builder()
            .id(123L)
            .userId(7L)
            .trainerId(9L)
            .slotId(11L)
            .reservationDate(LocalDate.of(2026, 5, 20))
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.HOLDING)
            .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
            .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
            .orderId("00000000-0000-0000-0000-000000000123")
            .orderKey("0x" + "0".repeat(61) + "123")
            .pendingAttemptToken("attempt-purchase")
            .bookedPriceAmount(50_000)
            .version(1L)
            .build();
    MarketplaceReservationActionState actionState =
        MarketplaceReservationActionState.builder()
            .id(21L)
            .reservationId(123L)
            .escrowId(10L)
            .actionType(ReservationEscrowAction.PURCHASE)
            .actorType(ReservationEscrowActorType.BUYER)
            .actorUserId(7L)
            .attemptNo(1)
            .attemptToken("attempt-purchase")
            .status(ReservationActionStateStatus.PREPARING)
            .build();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-unbound"))
        .willReturn(Optional.empty());
    given(loadReservationPort.findByIdWithLock(123L)).willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-unbound"))
        .willReturn(Optional.empty());
    given(loadReservationActionStatePort.findByIdWithLock(21L))
        .willReturn(Optional.of(actionState));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-unbound",
            "MARKETPLACE_CLASS_PURCHASE",
            "BUYER",
            reservation.getId(),
            "attempt-purchase",
            21L,
            "CANCELED",
            "phase b compensation"));

    then(saveReservationPort).shouldHaveNoInteractions();
    then(saveReservationEscrowPort).shouldHaveNoInteractions();
    ArgumentCaptor<MarketplaceReservationActionState> captor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationActionStateStatus.TERMINATED);
    assertThat(captor.getValue().getRetryable()).isTrue();
    assertThat(captor.getValue().getErrorCode()).isEqualTo("CANCELED");
  }

  private Reservation pendingCancelReservation() {
    return Reservation.builder()
        .id(123L)
        .userId(7L)
        .trainerId(9L)
        .slotId(11L)
        .reservationDate(LocalDate.of(2026, 5, 20))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.CANCEL_PENDING)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
        .orderId("00000000-0000-0000-0000-000000000123")
        .orderKey("0x" + "0".repeat(61) + "123")
        .currentExecutionIntentPublicId("intent-1")
        .pendingAttemptToken("attempt-1")
        .priorStatus(ReservationStatus.PENDING)
        .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
        .bookedPriceAmount(50_000)
        .version(1L)
        .build();
  }

  private Reservation pendingPurchaseReservation() {
    return Reservation.builder()
        .id(123L)
        .userId(7L)
        .trainerId(9L)
        .slotId(11L)
        .reservationDate(LocalDate.of(2026, 5, 20))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.HOLDING)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.PURCHASE_PENDING)
        .orderId("00000000-0000-0000-0000-000000000123")
        .orderKey("0x" + "0".repeat(61) + "123")
        .currentExecutionIntentPublicId("intent-purchase")
        .pendingAttemptToken("attempt-purchase")
        .bookedPriceAmount(50_000)
        .version(1L)
        .build();
  }

  private MarketplaceReservationActionState activeActionState() {
    return MarketplaceReservationActionState.builder()
        .id(20L)
        .reservationId(123L)
        .escrowId(10L)
        .actionType(ReservationEscrowAction.BUYER_CANCEL)
        .actorType(ReservationEscrowActorType.BUYER)
        .actorUserId(7L)
        .attemptNo(1)
        .attemptToken("attempt-1")
        .executionIntentPublicId("intent-1")
        .status(ReservationActionStateStatus.INTENT_BOUND)
        .build();
  }

  private MarketplaceReservationActionState purchaseActionState() {
    return MarketplaceReservationActionState.builder()
        .id(20L)
        .reservationId(123L)
        .escrowId(10L)
        .actionType(ReservationEscrowAction.PURCHASE)
        .actorType(ReservationEscrowActorType.BUYER)
        .actorUserId(7L)
        .attemptNo(1)
        .attemptToken("attempt-purchase")
        .executionIntentPublicId("intent-purchase")
        .status(ReservationActionStateStatus.INTENT_BOUND)
        .build();
  }

  private ReservationEscrowExecutionConfirmedCommand purchaseConfirmedCommand(
      Reservation reservation) {
    return new ReservationEscrowExecutionConfirmedCommand(
        "intent-purchase",
        "0xtx",
        "MARKETPLACE_CLASS_PURCHASE",
        "BUYER",
        reservation.getId(),
        reservation.getOrderKey(),
        CONTRACT_DEADLINE_EPOCH_SECONDS,
        CONTRACT_DEADLINE_EPOCH_SECONDS,
        reservation.sessionEndAt(),
        "attempt-purchase",
        20L);
  }

  private ReservationEscrowOrderView orderView(int state) {
    return new ReservationEscrowOrderView(
        "0x" + "0".repeat(61) + "123",
        "50000",
        "0x3333333333333333333333333333333333333333",
        CONTRACT_DEADLINE_EPOCH_SECONDS,
        state,
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222");
  }

  private static Stream<Arguments> purchaseConfirmedChainStates() {
    return Stream.of(
        Arguments.of(
            ReservationEscrowOrderView.STATE_CREATED,
            ReservationStatus.PENDING,
            ReservationEscrowStatus.LOCKED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_CONFIRMED,
            ReservationStatus.SETTLED,
            ReservationEscrowStatus.SETTLED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_CANCELLED,
            ReservationStatus.MANUAL_SYNC_REQUIRED,
            ReservationEscrowStatus.MANUAL_SYNC_REQUIRED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_ADMIN_SETTLED,
            ReservationStatus.AUTO_SETTLED,
            ReservationEscrowStatus.SETTLED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_ADMIN_REFUNDED,
            ReservationStatus.TIMEOUT_CANCELLED,
            ReservationEscrowStatus.REFUNDED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED,
            ReservationStatus.DEADLINE_REFUNDED,
            ReservationEscrowStatus.DEADLINE_REFUNDED),
        Arguments.of(
            9999,
            ReservationStatus.DEADLINE_SYNC_REQUIRED,
            ReservationEscrowStatus.DEADLINE_SYNC_REQUIRED));
  }

  private MarketplaceReservationEscrow escrowProjection() {
    return MarketplaceReservationEscrow.builder()
        .id(10L)
        .reservationId(123L)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .orderKey("0x" + "0".repeat(61) + "123")
        .build();
  }
}
