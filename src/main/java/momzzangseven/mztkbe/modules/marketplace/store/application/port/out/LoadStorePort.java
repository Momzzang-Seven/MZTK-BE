package momzzangseven.mztkbe.modules.marketplace.store.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.store.domain.model.TrainerStore;

/**
 * Outbound port for loading store data.
 *
 * <p>Hexagonal Architecture: This is an OUTPUT PORT that defines operations needed by the
 * application layer. Implemented by an adapter in the infrastructure layer, allowing the
 * application layer to remain independent of infrastructure details.
 */
public interface LoadStorePort {

  /**
   * Find a store by trainer's user ID.
   *
   * @param trainerId trainer's user ID
   * @return Optional containing the store if found
   */
  Optional<TrainerStore> findByTrainerId(Long trainerId);
}
