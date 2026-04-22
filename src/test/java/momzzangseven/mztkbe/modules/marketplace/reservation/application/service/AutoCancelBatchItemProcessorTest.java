package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @DisplayName("process - successfully processes auto-cancel")
  void process_SuccessfullyProcessesAutoCancel() {
    // Arrange
    Reservation reservation = mock(Reservation.class);
    Reservation cancelledReservation = mock(Reservation.class);
    String orderId = "order123";
    String txHash = "0xhash";
    Long trainerId = 100L;

    given(reservation.getOrderId()).willReturn(orderId);
    given(reservation.getTrainerId()).willReturn(trainerId);
    given(submitEscrowTransactionPort.submitAdminRefund(orderId)).willReturn(txHash);
    given(reservation.timeoutCancel(txHash)).willReturn(cancelledReservation);

    // Act
    sut.process(reservation);

    // Assert
    verify(submitEscrowTransactionPort).submitAdminRefund(orderId);
    verify(reservation).timeoutCancel(txHash);
    verify(saveReservationPort).save(cancelledReservation);
    verify(recordTrainerStrikePort).recordStrike(trainerId, TrainerStrikeEvent.REASON_TIMEOUT);
  }
}
