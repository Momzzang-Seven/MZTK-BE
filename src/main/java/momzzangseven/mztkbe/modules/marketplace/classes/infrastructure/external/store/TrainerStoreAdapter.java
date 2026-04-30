package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.external.store;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadTrainerStorePort;
import momzzangseven.mztkbe.modules.marketplace.store.application.port.out.LoadStorePort;
import momzzangseven.mztkbe.modules.marketplace.store.domain.model.TrainerStore;
import org.springframework.stereotype.Component;

/**
 * Adapter that satisfies the class module's {@link LoadTrainerStorePort} by delegating to the
 * existing store module's {@link LoadStorePort}.
 *
 * <p>This follows the cross-module dependency pattern defined in ARCHITECTURE.md: the class module
 * declares its need as an output port ({@code LoadTrainerStorePort}) and this adapter — the only
 * class that imports from the store module — implements it.
 *
 * <p>Both ports live in the same {@code marketplace} module, so the import is within module
 * boundaries here (store and class are co-located under marketplace). The adapter simply re-uses
 * the existing port rather than duplicating persistence logic.
 */
@Component
@RequiredArgsConstructor
public class TrainerStoreAdapter implements LoadTrainerStorePort {

  private final LoadStorePort loadStorePort;

  @Override
  public Optional<TrainerStore> findByTrainerId(Long trainerId) {
    return loadStorePort.findByTrainerId(trainerId);
  }
}
