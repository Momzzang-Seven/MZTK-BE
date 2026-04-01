package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreResult;

/** CommandHandler for creating or updating a trainer store. */
public interface UpsertStoreCommandHandler {

  /**
   * Create or update a trainer store.
   *
   * @param command store upsert command
   * @return result containing the persisted store ID
   */
  UpsertStoreResult execute(UpsertStoreCommand command);
}
