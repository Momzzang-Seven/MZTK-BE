package momzzangseven.mztkbe.modules.level.domain.vo;

/** FE-friendly grouping for level reward transaction states. */
public enum RewardTxPhase {
  PENDING,
  SUCCESS,
  FAILED;

  public static RewardTxPhase from(RewardTxStatus status) {
    if (status == RewardTxStatus.SUCCEEDED) {
      return SUCCESS;
    }
    if (status == RewardTxStatus.FAILED_ONCHAIN) {
      return FAILED;
    }
    return PENDING;
  }
}
