package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverExpiredMarketplaceAdminExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionRequestSource;
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
class RecoverExpiredMarketplaceAdminExecutionAttemptServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 21, 12, 0);

  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private SaveReservationActionStatePort saveReservationActionStatePort;
  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private SaveReservationEscrowPort saveReservationEscrowPort;

  private RecoverExpiredMarketplaceAdminExecutionAttemptService service;

  @BeforeEach
  void setUp() {
    service =
        new RecoverExpiredMarketplaceAdminExecutionAttemptService(
            loadReservationActionStatePort,
            saveReservationActionStatePort,
            loadReservationPort,
            saveReservationPort,
            loadReservationEscrowPort,
            saveReservationEscrowPort,
            ReservationTestTransactionPort.direct());
  }

  @Test
  void expiredUnboundAdminPreparationRestoresPriorStateAndClosesActionState() {
    MarketplaceReservationActionState actionState = expiredAdminActionState();
    Reservation reservation = adminRefundPendingReservation();
    given(loadReservationActionStatePort.findExpiredAdminPreparingAttemptsWithLock(NOW, 10))
        .willReturn(List.of(actionState));
    given(loadReservationPort.findByIdWithLock(1L)).willReturn(Optional.of(reservation));
    given(saveReservationPort.save(org.mockito.ArgumentMatchers.any()))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(loadReservationEscrowPort.findByReservationIdWithLock(1L))
        .willReturn(Optional.of(adminRefundPendingEscrow()));

    var result =
        service.execute(new RecoverExpiredMarketplaceAdminExecutionAttemptCommand(NOW, 10));

    assertThat(result.scanned()).isEqualTo(1);
    assertThat(result.recovered()).isEqualTo(1);
    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    Reservation restored = reservationCaptor.getValue();
    assertThat(restored.getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(restored.getEffectiveEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(restored.getPendingAttemptToken()).isNull();
    assertThat(restored.getPendingAction()).isNull();
    assertThat(restored.getPendingActionExpiresAt()).isNull();

    ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getStatus())
        .isEqualTo(ReservationActionStateStatus.PREPARATION_FAILED);
    assertThat(actionCaptor.getValue().getRetryable()).isTrue();
    assertThat(actionCaptor.getValue().getErrorCode()).isEqualTo("PREPARATION_EXPIRED");

    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(escrowCaptor.getValue().getLastFailureCode()).isEqualTo("PREPARATION_EXPIRED");
  }

  @Test
  void expiredAdminPreparationThatNoLongerMatchesReservationIsMarkedStale() {
    MarketplaceReservationActionState actionState = expiredAdminActionState();
    given(loadReservationActionStatePort.findExpiredAdminPreparingAttemptsWithLock(NOW, 10))
        .willReturn(List.of(actionState));
    given(loadReservationPort.findByIdWithLock(1L)).willReturn(Optional.of(pendingReservation()));

    var result =
        service.execute(new RecoverExpiredMarketplaceAdminExecutionAttemptCommand(NOW, 10));

    assertThat(result.skipped()).isEqualTo(1);
    then(saveReservationPort).shouldHaveNoInteractions();
    ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getStatus()).isEqualTo(ReservationActionStateStatus.STALE);
    assertThat(actionCaptor.getValue().getRetryable()).isFalse();
  }

  private MarketplaceReservationActionState expiredAdminActionState() {
    return MarketplaceReservationActionState.builder()
        .id(20L)
        .reservationId(1L)
        .escrowId(900L)
        .actionType(ReservationEscrowAction.ADMIN_REFUND)
        .actorType(ReservationEscrowActorType.ADMIN)
        .actorUserId(77L)
        .requestSource(ReservationActionRequestSource.MANUAL_ADMIN)
        .attemptNo(1)
        .attemptToken("attempt-1")
        .status(ReservationActionStateStatus.PREPARING)
        .priorReservationStatus(ReservationStatus.PENDING)
        .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
        .preparationExpiresAt(NOW.minusMinutes(1))
        .reasonCode("TRAINER_TIMEOUT")
        .build();
  }

  private Reservation adminRefundPendingReservation() {
    return pendingReservation().beginAdminRefundPending("attempt-1", NOW.minusMinutes(1));
  }

  private Reservation pendingReservation() {
    return Reservation.builder()
        .id(1L)
        .userId(10L)
        .trainerId(20L)
        .slotId(30L)
        .reservationDate(LocalDate.of(2026, 5, 23))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .build();
  }

  private MarketplaceReservationEscrow adminRefundPendingEscrow() {
    return MarketplaceReservationEscrow.builder()
        .id(900L)
        .reservationId(1L)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.ADMIN_REFUND_PENDING)
        .build();
  }
}
