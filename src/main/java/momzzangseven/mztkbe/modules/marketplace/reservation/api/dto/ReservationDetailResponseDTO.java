package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record ReservationDetailResponseDTO(
    Long reservationId,
    Long userId,
    Long trainerId,
    Long slotId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    int durationMinutes,
    ReservationStatus status,
    String userRequest,
    String orderId,
    String txHash,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static ReservationDetailResponseDTO from(GetReservationResult result) {
    return new ReservationDetailResponseDTO(
        result.reservationId(),
        result.userId(),
        result.trainerId(),
        result.slotId(),
        result.reservationDate(),
        result.reservationTime(),
        result.durationMinutes(),
        result.status(),
        result.userRequest(),
        result.orderId(),
        result.txHash(),
        result.createdAt(),
        result.updatedAt());
  }
}
