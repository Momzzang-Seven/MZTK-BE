package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationViewerActions;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Maps internal reservation/escrow state to the public reservation read-model status. */
public final class ReservationDisplayStatusMapper {

  private ReservationDisplayStatusMapper() {}

  public static ReservationDisplayStatus displayStatus(Reservation reservation) {
    if (reservation.getStatus() == ReservationStatus.HOLDING) {
      return reservation.getEffectiveEscrowStatus() == ReservationEscrowStatus.PURCHASE_PENDING
              || reservation.getCurrentExecutionIntentPublicId() != null
          ? ReservationDisplayStatus.PURCHASE_PENDING
          : ReservationDisplayStatus.PURCHASE_PREPARING;
    }
    return ReservationDisplayStatus.valueOf(reservation.getStatus().name());
  }

  public static ReservationStatus businessStatus(Reservation reservation) {
    return businessStatus(reservation.getStatus());
  }

  public static ReservationStatus businessStatus(ReservationStatus status) {
    return switch (status) {
      case HOLDING,
              PURCHASE_PREPARING,
              PURCHASE_PENDING,
              CANCEL_PENDING,
              REJECT_PENDING,
              CONFIRM_PENDING,
              DEADLINE_REFUND_PENDING,
              DEADLINE_RECOVERY_REQUIRED,
              DEADLINE_SYNC_REQUIRED,
              DEADLINE_REFUND_AVAILABLE ->
          null;
      default -> status;
    };
  }

  public static GetReservationResult detailResult(
      Reservation reservation,
      String classTitle,
      Integer priceAmount,
      String thumbnailFinalObjectKey,
      String trainerNickname,
      String userNickname,
      Long viewerId,
      ReservationExecutionResumeView web3Execution) {
    ReservationViewerActions viewerActions =
        ReservationViewerActionPolicy.resolve(reservation, viewerId, web3Execution);
    return new GetReservationResult(
        reservation.getId(),
        reservation.getUserId(),
        reservation.getTrainerId(),
        reservation.getSlotId(),
        reservation.getReservationDate(),
        reservation.getReservationTime(),
        reservation.getDurationMinutes(),
        displayStatus(reservation),
        businessStatus(reservation),
        reservation.getEffectiveEscrowStatus(),
        reservation.getUserRequest(),
        reservation.getOrderId(),
        reservation.getOrderKey(),
        reservation.getTxHash(),
        reservation.getContractDeadlineAt(),
        reservation.getContractDeadlineEpochSeconds(),
        reservation.getCreatedAt(),
        reservation.getUpdatedAt(),
        classTitle,
        priceAmount,
        trainerNickname,
        userNickname,
        thumbnailFinalObjectKey,
        viewerActions,
        web3Execution);
  }

  public static GetReservationResult detailResult(
      Reservation reservation,
      String classTitle,
      Integer priceAmount,
      String thumbnailFinalObjectKey,
      String trainerNickname,
      String userNickname,
      ReservationExecutionResumeView web3Execution) {
    return detailResult(
        reservation,
        classTitle,
        priceAmount,
        thumbnailFinalObjectKey,
        trainerNickname,
        userNickname,
        null,
        web3Execution);
  }

  public static ReservationSummaryResult summaryResult(
      Reservation reservation,
      String classTitle,
      Integer priceAmount,
      String thumbnailFinalObjectKey,
      String trainerNickname,
      String userNickname,
      Long viewerId,
      ReservationExecutionResumeView web3Execution) {
    ReservationViewerActions viewerActions =
        ReservationViewerActionPolicy.resolve(reservation, viewerId, web3Execution);
    return new ReservationSummaryResult(
        reservation.getId(),
        reservation.getSlotId(),
        reservation.getTrainerId(),
        reservation.getUserId(),
        reservation.getReservationDate(),
        reservation.getReservationTime(),
        reservation.getDurationMinutes(),
        displayStatus(reservation),
        businessStatus(reservation),
        reservation.getEffectiveEscrowStatus(),
        reservation.getUserRequest(),
        reservation.getOrderKey(),
        reservation.getContractDeadlineAt(),
        reservation.getContractDeadlineEpochSeconds(),
        classTitle,
        priceAmount,
        trainerNickname,
        userNickname,
        thumbnailFinalObjectKey,
        viewerActions,
        web3Execution);
  }

  public static ReservationSummaryResult summaryResult(
      Reservation reservation,
      String classTitle,
      Integer priceAmount,
      String thumbnailFinalObjectKey,
      String trainerNickname,
      String userNickname,
      ReservationExecutionResumeView web3Execution) {
    return summaryResult(
        reservation,
        classTitle,
        priceAmount,
        thumbnailFinalObjectKey,
        trainerNickname,
        userNickname,
        null,
        web3Execution);
  }
}
