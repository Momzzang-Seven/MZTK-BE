package momzzangseven.mztkbe.modules.verification.domain.vo;

/** Verification lifecycle states. */
public enum VerificationStatus {
  PENDING,
  ANALYZING,
  RETRY_SCHEDULED,
  VERIFIED,
  REJECTED,
  FAILED_FINAL;

  public boolean isActive() {
    return this == PENDING || this == ANALYZING || this == RETRY_SCHEDULED;
  }

  public boolean isTerminal() {
    return this == VERIFIED || this == REJECTED || this == FAILED_FINAL;
  }

  /** Returns whether the status blocks same-day resubmission. */
  public boolean canTransitionTo(VerificationStatus nextStatus) {
    if (nextStatus == null || this == nextStatus) {
      return false;
    }
    return switch (this) {
      case PENDING -> nextStatus == ANALYZING;
      case ANALYZING ->
          nextStatus == VERIFIED
              || nextStatus == REJECTED
              || nextStatus == RETRY_SCHEDULED
              || nextStatus == FAILED_FINAL;
      case RETRY_SCHEDULED -> nextStatus == ANALYZING;
      case VERIFIED, REJECTED, FAILED_FINAL -> false;
    };
  }
}
