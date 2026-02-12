package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;

public class Web3TransactionNotFoundException extends BusinessException {

  public Web3TransactionNotFoundException(Long transactionId) {
    super(
        Web3ErrorCode.WEB3_TRANSACTION_NOT_FOUND,
        "web3 transaction not found: id=" + transactionId);
  }
}
