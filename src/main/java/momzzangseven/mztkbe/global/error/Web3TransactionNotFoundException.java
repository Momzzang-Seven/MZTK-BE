package momzzangseven.mztkbe.global.error;

public class Web3TransactionNotFoundException extends BusinessException {

  public Web3TransactionNotFoundException(Long transactionId) {
    super(ErrorCode.WEB3_TRANSACTION_NOT_FOUND, "web3 transaction not found: id=" + transactionId);
  }
}
