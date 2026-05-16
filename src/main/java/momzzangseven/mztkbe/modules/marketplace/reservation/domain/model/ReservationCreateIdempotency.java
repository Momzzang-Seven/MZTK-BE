package momzzangseven.mztkbe.modules.marketplace.reservation.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;

/** Persisted lifecycle for idempotent marketplace reservation create requests. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReservationCreateIdempotency {

  private final Long id;
  private final Long buyerId;
  private final String keyHash;
  private final String payloadHash;
  private final ReservationCreateIdempotencyStatus status;
  private final Long reservationId;
  private final String currentExecutionIntentPublicId;
  private final String responseSnapshotJson;
  private final LocalDateTime expiresAt;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public static ReservationCreateIdempotency preparing(
      Long buyerId, String keyHash, String payloadHash, LocalDateTime expiresAt) {
    return ReservationCreateIdempotency.builder()
        .buyerId(buyerId)
        .keyHash(keyHash)
        .payloadHash(payloadHash)
        .status(ReservationCreateIdempotencyStatus.PREPARING)
        .expiresAt(expiresAt)
        .build();
  }

  public ReservationCreateIdempotency markIntentCreated(
      Long reservationId, String executionIntentPublicId) {
    return toBuilder()
        .status(ReservationCreateIdempotencyStatus.INTENT_CREATED)
        .reservationId(reservationId)
        .currentExecutionIntentPublicId(executionIntentPublicId)
        .build();
  }

  public ReservationCreateIdempotency markBound(
      Long reservationId, String executionIntentPublicId, String responseSnapshotJson) {
    return toBuilder()
        .status(ReservationCreateIdempotencyStatus.BOUND)
        .reservationId(reservationId)
        .currentExecutionIntentPublicId(executionIntentPublicId)
        .responseSnapshotJson(responseSnapshotJson)
        .build();
  }

  public ReservationCreateIdempotency markCompleted(String responseSnapshotJson) {
    return toBuilder()
        .status(ReservationCreateIdempotencyStatus.COMPLETED)
        .currentExecutionIntentPublicId(null)
        .responseSnapshotJson(responseSnapshotJson)
        .build();
  }

  public ReservationCreateIdempotency markFailed(String responseSnapshotJson) {
    return toBuilder()
        .status(ReservationCreateIdempotencyStatus.FAILED)
        .currentExecutionIntentPublicId(null)
        .responseSnapshotJson(responseSnapshotJson)
        .build();
  }

  public ReservationCreateIdempotency restart(String payloadHash, LocalDateTime expiresAt) {
    return toBuilder()
        .payloadHash(payloadHash)
        .status(ReservationCreateIdempotencyStatus.PREPARING)
        .reservationId(null)
        .currentExecutionIntentPublicId(null)
        .responseSnapshotJson(null)
        .expiresAt(expiresAt)
        .build();
  }
}
