package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

public enum QuestionRewardIntentStatus {
  PREPARE_REQUIRED,
  SUBMITTED,
  SUCCEEDED,
  FAILED_ONCHAIN,
  CANCELED;

  public boolean isFinalized() {
    return this == SUCCEEDED || this == CANCELED;
  }
}
