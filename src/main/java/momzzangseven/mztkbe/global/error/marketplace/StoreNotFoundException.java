package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a marketplace store is not found. */
public class StoreNotFoundException extends BusinessException {

  public StoreNotFoundException(Long trainerId) {
    super(
        ErrorCode.MARKETPLACE_STORE_NOT_FOUND,
        "Store not found for trainer ID: " + trainerId);
  }
}
