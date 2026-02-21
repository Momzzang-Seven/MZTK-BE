package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepare;

/** Persistence port for web3_transfer_prepares used by transfer application services. */
public interface TransferPreparePersistencePort {

  Optional<TransferPrepare> findFirstByIdempotencyKey(String idempotencyKey);

  Optional<TransferPrepare> findForUpdateByPrepareId(String prepareId);

  TransferPrepare create(TransferPrepare prepare);

  TransferPrepare update(TransferPrepare prepare);

  List<String> findPrepareIdsForCleanup(LocalDateTime cutoff, int batchSize);

  long deleteByPrepareIdIn(List<String> prepareIds);
}
