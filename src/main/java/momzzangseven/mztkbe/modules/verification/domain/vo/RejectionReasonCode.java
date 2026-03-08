package momzzangseven.mztkbe.modules.verification.domain.vo;

/** Business rejection reasons exposed by verification status APIs. */
public enum RejectionReasonCode {
  NOT_WORKOUT_PHOTO,
  LOW_CONFIDENCE,
  MISSING_VISIBLE_DATE,
  INVALID_VISIBLE_DATE,
  DATE_MISMATCH
}
