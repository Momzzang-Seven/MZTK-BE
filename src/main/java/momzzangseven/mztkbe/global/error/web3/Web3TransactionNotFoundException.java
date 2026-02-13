package momzzangseven.mztkbe.global.error.web3;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class Web3TransactionNotFoundException extends BusinessException {

  public Web3TransactionNotFoundException(Long transactionId) {
    super(ErrorCode.WEB3_TRANSACTION_NOT_FOUND, "web3 transaction not found: id=" + transactionId);
  }
}
