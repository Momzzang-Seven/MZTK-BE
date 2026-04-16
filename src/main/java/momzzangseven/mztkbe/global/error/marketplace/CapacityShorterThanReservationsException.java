package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when the new capacity is less than the current active reservation count. */
public class CapacityShorterThanReservationsException extends BusinessException {

  public CapacityShorterThanReservationsException(int activeReservations, int requestedCapacity) {
    super(
        ErrorCode.MARKETPLACE_CAPACITY_SHORTER_THAN_RESERVATIONS,
        "Requested capacity "
            + requestedCapacity
            + " is less than active reservations "
            + activeReservations);
  }
}
