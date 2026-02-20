package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferPreparePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferPreparePersistenceAdapter implements TransferPreparePersistencePort {

  private final Web3TransferPrepareJpaRepository repository;

  @Override
  public Optional<Web3TransferPrepareEntity> findFirstByIdempotencyKey(String idempotencyKey) {
    return repository.findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey);
  }

  @Override
  public Optional<Web3TransferPrepareEntity> findForUpdateByPrepareId(String prepareId) {
    return repository.findForUpdateByPrepareId(prepareId);
  }

  @Override
  public Web3TransferPrepareEntity save(Web3TransferPrepareEntity entity) {
    return repository.save(entity);
  }

  @Override
  public Web3TransferPrepareEntity saveAndFlush(Web3TransferPrepareEntity entity) {
    return repository.saveAndFlush(entity);
  }

  @Override
  public List<String> findPrepareIdsForCleanup(LocalDateTime cutoff, int batchSize) {
    return repository.findPrepareIdsForCleanup(cutoff, PageRequest.of(0, batchSize));
  }

  @Override
  public long deleteByPrepareIdIn(List<String> prepareIds) {
    return repository.deleteByPrepareIdIn(prepareIds);
  }
}
