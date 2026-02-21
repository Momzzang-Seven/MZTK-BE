package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.TransferPrepareRecord;

/** Persistence port for web3_transfer_prepares used by transfer application services. */
public interface TransferPreparePersistencePort {

  Optional<TransferPrepareRecord> findFirstByIdempotencyKey(String idempotencyKey);

  Optional<TransferPrepareRecord> findForUpdateByPrepareId(String prepareId);

  TransferPrepareRecord save(TransferPrepareRecord record);

  TransferPrepareRecord saveAndFlush(TransferPrepareRecord record);

  List<String> findPrepareIdsForCleanup(LocalDateTime cutoff, int batchSize);

  long deleteByPrepareIdIn(List<String> prepareIds);
}
