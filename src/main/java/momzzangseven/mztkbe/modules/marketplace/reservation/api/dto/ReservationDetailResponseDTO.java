package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * HTTP response DTO for the reservation detail endpoints.
 *
 * <p>Used by:
 *
 * <ul>
 *   <li>{@code GET /marketplace/reservations/{id}}
 *   <li>{@code GET /marketplace/trainer/reservations/{id}}
 * </ul>
 */
public record ReservationDetailResponseDTO(
    Long reservationId,
    Long userId,
    Long trainerId,
    Long slotId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    int durationMinutes,
    ReservationDisplayStatus status,
    ReservationStatus businessStatus,
    ReservationEscrowStatus escrowStatus,
    String userRequest,
    String orderId,
    String orderKey,
    String txHash,
    LocalDateTime contractDeadlineAt,
    Long contractDeadlineEpochSeconds,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String classTitle,
    Integer priceAmount,
    String trainerNickname,
    String userNickname,
    String thumbnailFinalObjectKey,
    String viewerAction,
    boolean viewerCanCancel,
    boolean viewerCanReject,
    boolean viewerCanComplete,
    boolean viewerCanClaimDeadlineRefund,
    boolean viewerCanRecover,
    ReservationWeb3ExecutionResponseDTO web3Execution) {

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
        result.businessStatus(),
        result.escrowStatus(),
        result.userRequest(),
        result.orderId(),
        result.orderKey(),
        result.txHash(),
        result.contractDeadlineAt(),
        result.contractDeadlineEpochSeconds(),
        result.createdAt(),
        result.updatedAt(),
        result.classTitle(),
        result.priceAmount(),
        result.trainerNickname(),
        result.userNickname(),
        result.thumbnailFinalObjectKey(),
        result.viewerActions().viewerAction(),
        result.viewerActions().viewerCanCancel(),
        result.viewerActions().viewerCanReject(),
        result.viewerActions().viewerCanComplete(),
        result.viewerActions().viewerCanClaimDeadlineRefund(),
        result.viewerActions().viewerCanRecover(),
        ReservationWeb3ExecutionResponseDTO.from(result.web3Execution()));
  }
}
