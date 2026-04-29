package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.event.EscrowDispatchEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoSettleBatchItemProcessorTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private SubmitEscrowTransactionPort submitEscrowTransactionPort;

  @InjectMocks private AutoSettleBatchItemProcessor sut;

  @Test
  @DisplayName(
      "process - DB 저장 먼저, 그 다음 escrow adminSettle 호출 (DB-first ordering + stale-read guard)")
  void process_DbFirstThenEscrow() {
    // Arrange: stale reservation passed in from batch read (no-lock)
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 1L;
    given(stale.getId()).willReturn(reservationId);

    // Fresh locked reservation returned by findByIdWithLock
    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    String orderId = "order123";
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getOrderId()).willReturn(orderId);
    given(fresh.getTrainerId()).willReturn(100L);
    given(fresh.getStatus()).willReturn(ReservationStatus.APPROVED);

    given(loadReservationPort.findByIdWithLock(reservationId)).willReturn(Optional.of(fresh));

    // DB-first: autoSettle called with sentinel hash
    Reservation settledWithSentinel = org.mockito.Mockito.mock(Reservation.class);
    given(fresh.autoSettle(EscrowDispatchEventListener.PENDING_TX_HASH))
        .willReturn(settledWithSentinel);
    given(saveReservationPort.save(settledWithSentinel)).willReturn(settledWithSentinel);

    // Escrow called after DB save
    String realTxHash = "0xhash";
    given(submitEscrowTransactionPort.submitAdminSettle(orderId)).willReturn(realTxHash);

    // txHash write-back
    Reservation settledWithRealTxHash = org.mockito.Mockito.mock(Reservation.class);
    given(settledWithSentinel.updateTxHash(realTxHash)).willReturn(settledWithRealTxHash);
    given(saveReservationPort.save(settledWithRealTxHash)).willReturn(settledWithRealTxHash);

    // Act
    sut.process(stale);

    // Assert — re-fetch with lock, then DB save before escrow call
    verify(loadReservationPort).findByIdWithLock(reservationId);

    InOrder order = inOrder(saveReservationPort, submitEscrowTransactionPort);
    order.verify(saveReservationPort).save(settledWithSentinel);
    order.verify(submitEscrowTransactionPort).submitAdminSettle(orderId);
    order.verify(saveReservationPort).save(settledWithRealTxHash);
  }

  @Test
  @DisplayName("process - APPROVED가 아닌 상태이면 처리를 건너뛴다 (stale-read guard)")
  void process_skipsIfNotApproved() {
    // Arrange: fresh locked row is already SETTLED (concurrent complete)
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 2L;
    given(stale.getId()).willReturn(reservationId);

    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getStatus()).willReturn(ReservationStatus.SETTLED);

    given(loadReservationPort.findByIdWithLock(reservationId)).willReturn(Optional.of(fresh));

    // Act
    sut.process(stale);

    // Assert — no DB save, no escrow call
    org.mockito.Mockito.verifyNoInteractions(saveReservationPort, submitEscrowTransactionPort);
  }
}
