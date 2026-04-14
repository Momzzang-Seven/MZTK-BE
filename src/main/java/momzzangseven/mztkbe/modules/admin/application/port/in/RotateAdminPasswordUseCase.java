package momzzangseven.mztkbe.modules.admin.application.port.in;

import momzzangseven.mztkbe.modules.admin.application.dto.RotateAdminPasswordCommand;

/** Input port for rotating an admin's own password. */
public interface RotateAdminPasswordUseCase {

  void execute(RotateAdminPasswordCommand command);
}
