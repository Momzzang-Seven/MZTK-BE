package momzzangseven.mztkbe.modules.marketplace.store.application.dto;

import momzzangseven.mztkbe.modules.marketplace.store.domain.model.TrainerStore;

/**
 * Result of store upsert operation.
 *
 * @param storeId persisted store ID
 */
public record UpsertStoreResult(Long storeId) {

  /**
   * Create from TrainerStore domain model.
   *
   * @param store saved TrainerStore domain model
   * @return UpsertStoreResult
   */
  public static UpsertStoreResult from(TrainerStore store) {
    return new UpsertStoreResult(store.getId());
  }
}
