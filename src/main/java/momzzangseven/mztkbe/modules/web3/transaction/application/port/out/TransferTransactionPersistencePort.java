package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;

/** Persistence port for transfer-initiated web3_transactions reads/writes. */
public interface TransferTransactionPersistencePort {

  Optional<TransferTransaction> findByIdempotencyKey(String idempotencyKey);

  Optional<TransferTransaction> findById(Long transactionId);

  TransferTransaction createAndFlush(TransferTransaction transaction);
}
