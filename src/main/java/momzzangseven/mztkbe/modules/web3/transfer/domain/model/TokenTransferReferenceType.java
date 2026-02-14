package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;

/** Reference types that are eligible for EIP-7702 user transfer flow. */
public enum TokenTransferReferenceType {
  USER_TO_USER,
  USER_TO_SERVER;

  public Web3ReferenceType toWeb3ReferenceType() {
    return Web3ReferenceType.valueOf(name());
  }
}
