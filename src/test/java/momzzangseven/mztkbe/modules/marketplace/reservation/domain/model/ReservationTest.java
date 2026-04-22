package momzzangseven.mztkbe.modules.marketplace.reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private Reservation createDefaultPendingReservation() {
        return Reservation.createPending(
            1L,
            2L,
            3L,
            LocalDate.of(2025, 1, 1),
            LocalTime.of(10, 0),
            60,
            "Request note",
            "order-123",
            "tx-123"
        );
    }

    @Test
    @DisplayName("createPending - successfully creates a pending reservation")
    void createPending_Success() {
        Reservation reservation = createDefaultPendingReservation();

        assertThat(reservation.getUserId()).isEqualTo(1L);
        assertThat(reservation.getTrainerId()).isEqualTo(2L);
        assertThat(reservation.getSlotId()).isEqualTo(3L);
        assertThat(reservation.getReservationDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(reservation.getReservationTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(reservation.getDurationMinutes()).isEqualTo(60);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getUserRequest()).isEqualTo("Request note");
        assertThat(reservation.getOrderId()).isEqualTo("order-123");
        assertThat(reservation.getTxHash()).isEqualTo("tx-123");
    }

    @Test
    @DisplayName("approve - transitions PENDING to APPROVED")
    void approve_Success() {
        Reservation pending = createDefaultPendingReservation();
        Reservation approved = pending.approve();

        assertThat(approved.getStatus()).isEqualTo(ReservationStatus.APPROVED);
        assertThat(approved.getTxHash()).isEqualTo("tx-123"); // txHash is preserved
    }

    @Test
    @DisplayName("approve - throws if not PENDING")
    void approve_ThrowsIfNotPending() {
        Reservation approved = createDefaultPendingReservation().approve();
        
        assertThatThrownBy(approved::approve)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition from APPROVED to APPROVED");
    }

    @Test
    @DisplayName("cancelByUser - transitions PENDING to USER_CANCELLED")
    void cancelByUser_Success() {
        Reservation pending = createDefaultPendingReservation();
        Reservation cancelled = pending.cancelByUser("cancel-tx");

        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.USER_CANCELLED);
        assertThat(cancelled.getTxHash()).isEqualTo("cancel-tx");
    }

    @Test
    @DisplayName("reject - transitions PENDING to REJECTED")
    void reject_Success() {
        Reservation pending = createDefaultPendingReservation();
        Reservation rejected = pending.reject("reject-tx");

        assertThat(rejected.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(rejected.getTxHash()).isEqualTo("reject-tx");
    }

    @Test
    @DisplayName("complete - transitions APPROVED to SETTLED")
    void complete_Success() {
        Reservation approved = createDefaultPendingReservation().approve();
        Reservation settled = approved.complete("confirm-tx");

        assertThat(settled.getStatus()).isEqualTo(ReservationStatus.SETTLED);
        assertThat(settled.getTxHash()).isEqualTo("confirm-tx");
    }

    @Test
    @DisplayName("timeoutCancel - transitions PENDING to TIMEOUT_CANCELLED")
    void timeoutCancel_Success() {
        Reservation pending = createDefaultPendingReservation();
        Reservation timeoutCancelled = pending.timeoutCancel("refund-tx");

        assertThat(timeoutCancelled.getStatus()).isEqualTo(ReservationStatus.TIMEOUT_CANCELLED);
        assertThat(timeoutCancelled.getTxHash()).isEqualTo("refund-tx");
    }

    @Test
    @DisplayName("autoSettle - transitions APPROVED to AUTO_SETTLED")
    void autoSettle_Success() {
        Reservation approved = createDefaultPendingReservation().approve();
        Reservation autoSettled = approved.autoSettle("admin-settle-tx");

        assertThat(autoSettled.getStatus()).isEqualTo(ReservationStatus.AUTO_SETTLED);
        assertThat(autoSettled.getTxHash()).isEqualTo("admin-settle-tx");
    }

    @Test
    @DisplayName("isOwnedByUser - checks ownership correctly")
    void isOwnedByUser_Correctly() {
        Reservation reservation = createDefaultPendingReservation();
        
        assertThat(reservation.isOwnedByUser(1L)).isTrue();
        assertThat(reservation.isOwnedByUser(999L)).isFalse();
    }

    @Test
    @DisplayName("isOwnedByTrainer - checks ownership correctly")
    void isOwnedByTrainer_Correctly() {
        Reservation reservation = createDefaultPendingReservation();
        
        assertThat(reservation.isOwnedByTrainer(2L)).isTrue();
        assertThat(reservation.isOwnedByTrainer(999L)).isFalse();
    }

    @Test
    @DisplayName("sessionEndAt - computes end time correctly")
    void sessionEndAt_ComputesCorrectly() {
        Reservation reservation = createDefaultPendingReservation();
        LocalDateTime endAt = reservation.sessionEndAt();
        
        // 2025-01-01T10:00 + 60 minutes = 2025-01-01T11:00
        assertThat(endAt).isEqualTo(LocalDateTime.of(2025, 1, 1, 11, 0));
    }
}
