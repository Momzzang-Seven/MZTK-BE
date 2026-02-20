package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;

/** Persistence port for web3_transfer_prepares used by transfer application services. */
public interface TransferPreparePersistencePort {

  Optional<Web3TransferPrepareEntity> findFirstByIdempotencyKey(String idempotencyKey);

  Optional<Web3TransferPrepareEntity> findForUpdateByPrepareId(String prepareId);

  Web3TransferPrepareEntity save(Web3TransferPrepareEntity entity);

  Web3TransferPrepareEntity saveAndFlush(Web3TransferPrepareEntity entity);

  List<String> findPrepareIdsForCleanup(LocalDateTime cutoff, int batchSize);

  long deleteByPrepareIdIn(List<String> prepareIds);
}
