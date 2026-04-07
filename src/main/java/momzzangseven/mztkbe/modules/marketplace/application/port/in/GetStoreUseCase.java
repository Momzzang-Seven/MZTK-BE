package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreResult;

/** UseCase for retrieving a trainer's store. */
public interface GetStoreUseCase {

  /**
   * Execute store retrieval.
   *
   * @param command store retrieval command
   * @return result containing the store information
   */
  GetStoreResult execute(GetStoreCommand command);
}
