package momzzangseven.mztkbe.modules.location.application.port.out;

import momzzangseven.mztkbe.modules.location.domain.model.Location;

/** Output port for saving location object from database implemented in Infrastructure layer. */
public interface SaveLocationPort {

  /**
   * Save location
   *
   * @param location
   * @return Saved location object
   */
  Location save(Location location);
}
