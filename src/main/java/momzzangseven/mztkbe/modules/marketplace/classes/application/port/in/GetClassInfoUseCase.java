package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;

/**
 * Input port that exposes class aggregate lookup to other modules.
 *
 * <p>Cross-module callers (e.g., the {@code reservation} module) must use this interface instead of
 * directly referencing the output port {@code LoadClassPort}. This keeps the dependency direction
 * correct: only {@code application/port/in/} is the public API surface of a module.
 */
public interface GetClassInfoUseCase {

  /**
   * Find a class by its ID.
   *
   * @param classId class ID
   * @return Optional containing the class aggregate if found
   */
  Optional<MarketplaceClass> findById(Long classId);
}
