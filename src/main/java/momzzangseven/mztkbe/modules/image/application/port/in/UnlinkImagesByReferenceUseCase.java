package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.UnlinkImagesByReferenceCommand;

/**
 * Input port for unlinking all images associated with a deleted reference entity. Triggered by
 * domain events (e.g., PostDeletedEvent) after the entity transaction commits.
 *
 * <p>Owned by the image module; consumed by infrastructure event handlers in other modules (e.g.,
 * post, marketplace).
 */
public interface UnlinkImagesByReferenceUseCase {
  void execute(UnlinkImagesByReferenceCommand command);
}
