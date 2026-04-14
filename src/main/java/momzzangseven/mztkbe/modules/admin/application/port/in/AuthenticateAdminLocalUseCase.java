package momzzangseven.mztkbe.modules.admin.application.port.in;

import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalResult;

/**
 * Input port for authenticating an admin account using local credentials (loginId + password).
 * Verifies the credentials and updates the last-login timestamp.
 */
public interface AuthenticateAdminLocalUseCase {

  AuthenticateAdminLocalResult execute(AuthenticateAdminLocalCommand command);
}
