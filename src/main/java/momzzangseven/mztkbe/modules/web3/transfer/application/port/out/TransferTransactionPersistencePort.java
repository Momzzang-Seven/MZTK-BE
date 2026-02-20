package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;

/** Persistence port for transfer-initiated web3_transactions reads/writes. */
public interface TransferTransactionPersistencePort {

  Optional<Web3TransactionEntity> findByIdempotencyKey(String idempotencyKey);

  Optional<Web3TransactionEntity> findById(Long transactionId);

  Web3TransactionEntity saveAndFlush(Web3TransactionEntity entity);
}
