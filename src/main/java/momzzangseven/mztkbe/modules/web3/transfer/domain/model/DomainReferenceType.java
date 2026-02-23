package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

/** Business domain type for transfer preparation requests. */
public enum DomainReferenceType {
  QUESTION_REWARD(TokenTransferReferenceType.USER_TO_USER),
  LEVEL_UP_REWARD(TokenTransferReferenceType.SERVER_TO_USER);

  private final TokenTransferReferenceType transferReferenceType;

  DomainReferenceType(TokenTransferReferenceType transferReferenceType) {
    this.transferReferenceType = transferReferenceType;
  }

  public TokenTransferReferenceType toTokenTransferReferenceType() {
    return transferReferenceType;
  }

  public boolean isUserPrepareSupported() {
    return this == QUESTION_REWARD;
  }
}
