package momzzangseven.mztkbe.modules.level.domain.vo;

/** Level module local projection of reward transaction status. */
public enum RewardTxStatus {
  CREATED,
  SIGNED,
  PENDING,
  SUCCEEDED,
  FAILED_ONCHAIN,
  UNCONFIRMED;

  public boolean isPendingLike() {
    return this == CREATED || this == SIGNED || this == PENDING || this == UNCONFIRMED;
  }
}
