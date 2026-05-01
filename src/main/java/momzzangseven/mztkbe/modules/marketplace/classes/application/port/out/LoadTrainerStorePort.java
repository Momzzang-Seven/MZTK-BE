package momzzangseven.mztkbe.modules.marketplace.classes.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.store.domain.model.TrainerStore;

/**
 * Output port for verifying that a trainer has an active store registered.
 *
 * <p>This is a cross-module dependency: the marketplace class module needs to confirm that the
 * trainer has a store before allowing class registration. Implemented by {@link
 * momzzangseven.mztkbe.modules.marketplace.infrastructure.external.store.TrainerStoreAdapter},
 * which delegates to the existing {@link
 * momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadStorePort}.
 */
public interface LoadTrainerStorePort {

  /**
   * Find the trainer's store by trainer ID.
   *
   * @param trainerId trainer's user ID
   * @return Optional containing the store domain model if found
   */
  Optional<TrainerStore> findByTrainerId(Long trainerId);
}
