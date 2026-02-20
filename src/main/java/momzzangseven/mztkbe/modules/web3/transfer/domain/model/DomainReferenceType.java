package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;

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

  public Web3ReferenceType toWeb3ReferenceType() {
    return transferReferenceType.toWeb3ReferenceType();
  }

  public boolean isUserPrepareSupported() {
    return this == QUESTION_REWARD;
  }
}
