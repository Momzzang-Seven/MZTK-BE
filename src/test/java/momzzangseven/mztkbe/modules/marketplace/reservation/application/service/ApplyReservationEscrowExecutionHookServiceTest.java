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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionConfirmedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplyReservationEscrowExecutionHookServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private SaveReservationActionStatePort saveReservationActionStatePort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private SaveReservationEscrowPort saveReservationEscrowPort;

  private ApplyReservationEscrowExecutionHookService service;

  @BeforeEach
  void setUp() {
    service =
        new ApplyReservationEscrowExecutionHookService(
            loadReservationPort,
            saveReservationPort,
            Clock.fixed(Instant.parse("2026-05-16T00:00:00Z"), ZoneOffset.UTC),
            null,
            null,
            null,
            null);
    service.setTransactionPort(ReservationTestTransactionPort.direct());
    service.setActionStatePorts(loadReservationActionStatePort, saveReservationActionStatePort);
    service.setEscrowProjectionPorts(loadReservationEscrowPort, saveReservationEscrowPort);
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
