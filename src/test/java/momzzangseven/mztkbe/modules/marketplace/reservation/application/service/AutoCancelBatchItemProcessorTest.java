package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
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

  @Mock private SaveReservationPort saveReservationPort;
  @Mock private SubmitEscrowTransactionPort submitEscrowTransactionPort;
  @Mock private RecordTrainerStrikePort recordTrainerStrikePort;

  @InjectMocks private AutoCancelBatchItemProcessor sut;

  @Test
  @DisplayName("process - DB 저장 먼저, 그 다음 escrow adminRefund 호출 (DB-first ordering)")
  void process_DbFirstThenEscrow() {
    // Arrange
    Reservation reservation = mock(Reservation.class);
    Reservation cancelledWithSentinel = mock(Reservation.class);
    Reservation cancelledWithRealTxHash = mock(Reservation.class);
    String orderId = "order123";
    String realTxHash = "0xhash";
    Long trainerId = 100L;
    Long reservationId = 1L;

    given(reservation.getOrderId()).willReturn(orderId);
    given(reservation.getTrainerId()).willReturn(trainerId);
    given(reservation.getId()).willReturn(reservationId);
    // DB-first: timeoutCancel called with sentinel hash
    given(reservation.timeoutCancel(EscrowDispatchEventListener.PENDING_TX_HASH))
        .willReturn(cancelledWithSentinel);
    given(saveReservationPort.save(cancelledWithSentinel)).willReturn(cancelledWithSentinel);
    // Escrow called after DB save
    given(submitEscrowTransactionPort.submitAdminRefund(orderId)).willReturn(realTxHash);
    // txHash write-back
    given(cancelledWithSentinel.updateTxHash(realTxHash)).willReturn(cancelledWithRealTxHash);
    given(saveReservationPort.save(cancelledWithRealTxHash)).willReturn(cancelledWithRealTxHash);

    // Act
    sut.process(reservation);

    // Assert — DB save before escrow call
    InOrder order = inOrder(saveReservationPort, submitEscrowTransactionPort);
    order.verify(saveReservationPort).save(cancelledWithSentinel);
    order.verify(submitEscrowTransactionPort).submitAdminRefund(orderId);
    order.verify(saveReservationPort).save(cancelledWithRealTxHash);

    verify(recordTrainerStrikePort).recordStrike(trainerId, TrainerStrikeEvent.REASON_TIMEOUT);
  }
}
