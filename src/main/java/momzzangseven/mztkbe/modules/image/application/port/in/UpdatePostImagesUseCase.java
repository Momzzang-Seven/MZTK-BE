package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.UpdatePostImagesCommand;

/**
 * Input port for synchronously updating the image set of a post during a post update operation.
 * Called directly by {@code PostProcessService} within the same transaction.
 *
 * <p>Owned by the image module. The post module depends on this UseCase interface (not the impl),
 * maintaining a unidirectional dependency: post.application → image.application.port.in.
 */
public interface UpdatePostImagesUseCase {
  void execute(UpdatePostImagesCommand command);
}
