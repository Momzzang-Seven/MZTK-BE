package momzzangseven.mztkbe.modules.marketplace.reservation.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionRequestSource;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** One prepare/sign/execute attempt for a marketplace reservation escrow action. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketplaceReservationActionState {

  private final Long id;
  private final Long reservationId;
  private final Long escrowId;
  private final ReservationEscrowAction actionType;
  private final ReservationEscrowActorType actorType;
  private final Long actorUserId;
  private final ReservationActionRequestSource requestSource;
  private final Integer attemptNo;
  private final String attemptToken;
  private final String executionIntentPublicId;
  private final String rootIdempotencyKey;
  private final String payloadHash;
  private final ReservationActionStateStatus status;
  private final Long expectedReservationVersion;
  private final ReservationStatus expectedReservationStatus;
  private final ReservationEscrowStatus expectedEscrowStatus;
  private final ReservationStatus priorReservationStatus;
  private final ReservationEscrowStatus priorEscrowStatus;
  private final LocalDateTime preparationExpiresAt;
  private final LocalDateTime serverSignatureSignedAt;
  private final LocalDateTime serverSignatureExpiresAt;
  private final String actionReason;
  private final String reasonCode;
  private final String memo;
  private final Boolean retryable;
  private final String errorCode;
  private final String errorReason;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;
}
