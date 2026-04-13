package momzzangseven.mztkbe.modules.admin.application.port.out;

import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;

/** Output port for generating admin login credentials (login ID and password). */
public interface GenerateCredentialPort {

  /** Generate a new login ID and password pair. */
  GeneratedAdminCredentials generate();

  /** Generate a new password only (for peer-reset). */
  String generatePasswordOnly();
}
