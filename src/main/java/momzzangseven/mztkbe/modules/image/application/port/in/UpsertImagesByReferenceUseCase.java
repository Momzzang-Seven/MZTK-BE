package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;

/** Input port for synchronously upserting the image set of other modules. */
public interface UpsertImagesByReferenceUseCase {
  void execute(UpsertImagesByReferenceCommand command);
}
