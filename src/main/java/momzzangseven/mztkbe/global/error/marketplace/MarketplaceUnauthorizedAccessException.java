package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a trainer attempts to modify a class they do not own. */
public class MarketplaceUnauthorizedAccessException extends BusinessException {

  public MarketplaceUnauthorizedAccessException(Long classId, Long trainerId) {
    super(
        ErrorCode.MARKETPLACE_UNAUTHORIZED_ACCESS,
        "Trainer " + trainerId + " does not own class " + classId);
  }
}
