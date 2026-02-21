package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.TransferTransactionRecord;

/** Persistence port for transfer-initiated web3_transactions reads/writes. */
public interface TransferTransactionPersistencePort {

  Optional<TransferTransactionRecord> findByIdempotencyKey(String idempotencyKey);

  Optional<TransferTransactionRecord> findById(Long transactionId);

  TransferTransactionRecord saveAndFlush(TransferTransactionRecord record);
}
