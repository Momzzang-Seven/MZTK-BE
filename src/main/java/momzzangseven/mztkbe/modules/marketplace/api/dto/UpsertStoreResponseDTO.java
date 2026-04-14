package momzzangseven.mztkbe.modules.marketplace.api.dto;

import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreResult;

/**
 * Response DTO for store upsert operation.
 *
 * @param storeId the persisted store ID
 */
public record UpsertStoreResponseDTO(Long storeId) {

  /**
   * Create from UpsertStoreResult.
   *
   * @param result application layer result
   * @return UpsertStoreResponseDTO
   */
  public static UpsertStoreResponseDTO from(UpsertStoreResult result) {
    return new UpsertStoreResponseDTO(result.storeId());
  }
}
