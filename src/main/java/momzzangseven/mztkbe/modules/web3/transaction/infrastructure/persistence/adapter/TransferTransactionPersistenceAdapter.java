package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferTransactionPersistencePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferTransactionPersistenceAdapter implements TransferTransactionPersistencePort {

  private final Web3TransactionJpaRepository repository;

  @Override
  public Optional<Web3TransactionEntity> findByIdempotencyKey(String idempotencyKey) {
    return repository.findByIdempotencyKey(idempotencyKey);
  }

  @Override
  public Optional<Web3TransactionEntity> findById(Long transactionId) {
    return repository.findById(transactionId);
  }

  @Override
  public Web3TransactionEntity saveAndFlush(Web3TransactionEntity entity) {
    return repository.saveAndFlush(entity);
  }
}
