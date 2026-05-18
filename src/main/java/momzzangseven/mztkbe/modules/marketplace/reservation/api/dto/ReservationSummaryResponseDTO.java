package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * HTTP response DTO for the reservation list endpoints.
 *
 * <p>Used by:
 *
 * <ul>
 *   <li>{@code GET /marketplace/me/reservations}
 *   <li>{@code GET /marketplace/trainer/reservations}
 * </ul>
 *
 * <p>{@code userNickname} is the reserving user's display name. It is populated on the trainer-list
 * path (so the trainer can see who made each booking) and is {@code null} on the user-list path (a
 * user's own nickname is not needed when viewing their own reservation history).
 */
public record ReservationSummaryResponseDTO(
    Long reservationId,
    Long slotId,
    Long trainerId,
    Long userId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    int durationMinutes,
    ReservationDisplayStatus status,
    ReservationStatus businessStatus,
    ReservationEscrowStatus escrowStatus,
    String userRequest,
    String orderKey,
    LocalDateTime contractDeadlineAt,
    Long contractDeadlineEpochSeconds,
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

  public static ReservationSummaryResponseDTO from(ReservationSummaryResult result) {
    return new ReservationSummaryResponseDTO(
        result.reservationId(),
        result.slotId(),
        result.trainerId(),
        result.userId(),
        result.reservationDate(),
        result.reservationTime(),
        result.durationMinutes(),
        result.status(),
        result.businessStatus(),
        result.escrowStatus(),
        result.userRequest(),
        result.orderKey(),
        result.contractDeadlineAt(),
        result.contractDeadlineEpochSeconds(),
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
