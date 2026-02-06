package momzzangseven.mztkbe.modules.location.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.location.domain.model.Location;

/** Output port for loading location object from database implemented in Infrastructure layer. */
public interface LoadLocationPort {

  /**
   * find by location id
   *
   * @param locationId
   * @return Optional object of single location
   */
  Optional<Location> findByLocationId(Long locationId);

  /**
   * find by user id
   *
   * @param userId
   * @return list of locations registered by specific user
   */
  List<Location> findByUserId(Long userId);
}
