package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

/** Port for claiming transaction work with lock TTL semantics. */
public interface LoadTransactionWorkPort {

  List<TransactionWorkItem> claimByStatus(
      Web3TxStatus status, int limit, String workerId, Duration claimTtl);

  record TransactionWorkItem(
      Long transactionId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei,
      String txHash,
      String signedRawTx,
      String failureReason) {}
}
