package momzzangseven.mztkbe.modules.admin.application.port.in;

import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordResult;

/** Input port for resetting another admin's password (peer-reset). */
public interface ResetPeerAdminPasswordUseCase {

  ResetPeerAdminPasswordResult execute(ResetPeerAdminPasswordCommand command);
}
