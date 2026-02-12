package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public interface LoadTransactionPort {

  Optional<TransactionSnapshot> loadById(Long transactionId);

  record TransactionSnapshot(
      Long transactionId, Web3TxStatus status, String txHash, String failureReason) {}
}
