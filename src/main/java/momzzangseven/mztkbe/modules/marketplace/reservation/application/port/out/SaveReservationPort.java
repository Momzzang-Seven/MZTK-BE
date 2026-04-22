package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;

/** Output port for persisting reservation state changes. */
public interface SaveReservationPort {

  /**
   * Persist a new or updated reservation domain model.
   *
   * @param reservation the domain model to save
   * @return the saved domain model with generated/updated fields (id, version, updatedAt)
   */
  Reservation save(Reservation reservation);
}
