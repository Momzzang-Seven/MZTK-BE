package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoSettleBatchItemProcessorTest {

    @Mock
    private SaveReservationPort saveReservationPort;
    @Mock
    private SubmitEscrowTransactionPort submitEscrowTransactionPort;

    @InjectMocks
    private AutoSettleBatchItemProcessor sut;

    @Test
    @DisplayName("process - successfully processes auto-settle")
    void process_SuccessfullyProcessesAutoSettle() {
        // Arrange
        Reservation reservation = mock(Reservation.class);
        Reservation settledReservation = mock(Reservation.class);
        String orderId = "order123";
        String txHash = "0xhash";

        given(reservation.getOrderId()).willReturn(orderId);
        given(submitEscrowTransactionPort.submitAdminSettle(orderId)).willReturn(txHash);
        given(reservation.autoSettle(txHash)).willReturn(settledReservation);

        // Act
        sut.process(reservation);

        // Assert
        verify(submitEscrowTransactionPort).submitAdminSettle(orderId);
        verify(reservation).autoSettle(txHash);
        verify(saveReservationPort).save(settledReservation);
    }
}
