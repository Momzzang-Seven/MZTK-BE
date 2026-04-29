package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.event.EscrowDispatchEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoCancelBatchItemProcessorTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private SubmitEscrowTransactionPort submitEscrowTransactionPort;
  @Mock private RecordTrainerStrikePort recordTrainerStrikePort;

  @InjectMocks private AutoCancelBatchItemProcessor sut;

  @Test
  @DisplayName(
      "process - DB 저장 먼저, 그 다음 escrow adminRefund 호출 (DB-first ordering + stale-read guard)")
  void process_DbFirstThenEscrow() {
    // Arrange: stale reservation passed in from batch read (no-lock)
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 1L;
    given(stale.getId()).willReturn(reservationId);

    // Fresh locked reservation returned by findByIdWithLock (re-fetch guard)
    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    String orderId = "order123";
    Long trainerId = 100L;
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getOrderId()).willReturn(orderId);
    given(fresh.getTrainerId()).willReturn(trainerId);
    given(fresh.getStatus()).willReturn(ReservationStatus.PENDING);

    given(loadReservationPort.findByIdWithLock(reservationId)).willReturn(Optional.of(fresh));

    // DB-first: timeoutCancel called with sentinel hash
    Reservation cancelledWithSentinel = org.mockito.Mockito.mock(Reservation.class);
    given(fresh.timeoutCancel(EscrowDispatchEventListener.PENDING_TX_HASH))
        .willReturn(cancelledWithSentinel);
    given(saveReservationPort.save(cancelledWithSentinel)).willReturn(cancelledWithSentinel);

    // Escrow called after DB save
    String realTxHash = "0xhash";
    given(submitEscrowTransactionPort.submitAdminRefund(orderId)).willReturn(realTxHash);

    // txHash write-back
    Reservation cancelledWithRealTxHash = org.mockito.Mockito.mock(Reservation.class);
    given(cancelledWithSentinel.updateTxHash(realTxHash)).willReturn(cancelledWithRealTxHash);
    given(saveReservationPort.save(cancelledWithRealTxHash)).willReturn(cancelledWithRealTxHash);

    // Act
    sut.process(stale);

    // Assert — re-fetch with lock, then DB save before escrow call
    verify(loadReservationPort).findByIdWithLock(reservationId);

    InOrder order = inOrder(saveReservationPort, submitEscrowTransactionPort);
    order.verify(saveReservationPort).save(cancelledWithSentinel);
    order.verify(submitEscrowTransactionPort).submitAdminRefund(orderId);
    order.verify(saveReservationPort).save(cancelledWithRealTxHash);

    verify(recordTrainerStrikePort).recordStrike(trainerId, TrainerStrikeEvent.REASON_TIMEOUT);
  }

  @Test
  @DisplayName("process - PENDING이 아닌 상태이면 처리를 건너뛴다 (stale-read guard)")
  void process_skipsIfNotPending() {
    // Arrange: stale is PENDING, but fresh (locked) turns out to be USER_CANCELLED
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 2L;
    given(stale.getId()).willReturn(reservationId);

    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getStatus()).willReturn(ReservationStatus.USER_CANCELLED);

    given(loadReservationPort.findByIdWithLock(reservationId)).willReturn(Optional.of(fresh));

    // Act
    sut.process(stale);

    // Assert — no DB save, no escrow call, no strike
    org.mockito.Mockito.verifyNoInteractions(
        saveReservationPort, submitEscrowTransactionPort, recordTrainerStrikePort);
  }
}
