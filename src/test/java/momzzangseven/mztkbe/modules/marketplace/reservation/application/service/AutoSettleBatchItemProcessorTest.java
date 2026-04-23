package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
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

  @Mock private SaveReservationPort saveReservationPort;
  @Mock private SubmitEscrowTransactionPort submitEscrowTransactionPort;

  @InjectMocks private AutoSettleBatchItemProcessor sut;

  @Test
  @DisplayName("process - DB 저장 먼저, 그 다음 escrow adminSettle 호출 (DB-first ordering)")
  void process_DbFirstThenEscrow() {
    // Arrange
    Reservation reservation = mock(Reservation.class);
    Reservation settledWithSentinel = mock(Reservation.class);
    Reservation settledWithRealTxHash = mock(Reservation.class);
    String orderId = "order123";
    String realTxHash = "0xhash";

    given(reservation.getOrderId()).willReturn(orderId);
    given(reservation.getTrainerId()).willReturn(100L);
    // DB-first: autoSettle called with sentinel hash
    given(reservation.autoSettle(EscrowDispatchEventListener.PENDING_TX_HASH))
        .willReturn(settledWithSentinel);
    given(saveReservationPort.save(settledWithSentinel)).willReturn(settledWithSentinel);
    // Escrow called after DB save
    given(submitEscrowTransactionPort.submitAdminSettle(orderId)).willReturn(realTxHash);
    // txHash write-back
    given(settledWithSentinel.updateTxHash(realTxHash)).willReturn(settledWithRealTxHash);
    given(saveReservationPort.save(settledWithRealTxHash)).willReturn(settledWithRealTxHash);

    // Act
    sut.process(reservation);

    // Assert — DB save before escrow call
    InOrder order = inOrder(saveReservationPort, submitEscrowTransactionPort);
    order.verify(saveReservationPort).save(settledWithSentinel);
    order.verify(submitEscrowTransactionPort).submitAdminSettle(orderId);
    order.verify(saveReservationPort).save(settledWithRealTxHash);
  }
}
