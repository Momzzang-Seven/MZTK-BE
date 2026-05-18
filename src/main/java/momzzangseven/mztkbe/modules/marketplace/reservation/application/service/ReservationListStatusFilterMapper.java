package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Transitional mapper from public list filters to the current stored reservation status. */
public final class ReservationListStatusFilterMapper {

  private ReservationListStatusFilterMapper() {}

  public static ReservationStatus toStoredStatus(ReservationListStatusFilter filter) {
    return filter == null ? null : ReservationStatus.valueOf(filter.name());
  }
}
