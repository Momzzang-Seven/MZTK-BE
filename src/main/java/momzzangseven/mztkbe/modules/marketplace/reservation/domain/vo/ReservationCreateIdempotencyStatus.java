package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/** Persisted lifecycle for reservation create idempotency before a stable order exists. */
public enum ReservationCreateIdempotencyStatus {
  PREPARING,
  INTENT_CREATED,
  BOUND,
  COMPLETED,
  FAILED
}
