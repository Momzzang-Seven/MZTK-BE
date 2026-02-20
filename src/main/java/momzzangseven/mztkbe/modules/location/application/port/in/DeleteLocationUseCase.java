package momzzangseven.mztkbe.modules.location.application.port.in;

import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationResult;

/**
 * Delete Location Use Case
 *
 * <p>User can hard delete his/her already registered location by him/herself via this port.
 */
public interface DeleteLocationUseCase {

  /**
   * Execute location deletion(Hard Delete)
   *
   * @param command
   * @return Deletion Result
   */
  DeleteLocationResult execute(DeleteLocationCommand command);
}
