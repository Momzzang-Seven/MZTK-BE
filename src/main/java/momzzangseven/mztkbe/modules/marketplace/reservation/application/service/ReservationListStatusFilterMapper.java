package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Maps public list filters to stored status query hints and display-status predicates. */
public final class ReservationListStatusFilterMapper {

  private ReservationListStatusFilterMapper() {}

  public static ReservationStatus toStoredStatus(ReservationListStatusFilter filter) {
    if (filter == null) {
      return null;
    }
    return switch (filter) {
      case PURCHASE_PREPARING, PURCHASE_PENDING -> ReservationStatus.HOLDING;
      default -> ReservationStatus.valueOf(filter.name());
    };
  }

  public static boolean matchesDisplayStatus(
      Reservation reservation, ReservationListStatusFilter filter) {
    return filter == null
        || ReservationDisplayStatusMapper.displayStatus(reservation).name().equals(filter.name());
  }
}
