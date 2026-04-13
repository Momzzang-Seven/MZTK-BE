package momzzangseven.mztkbe.modules.admin.application.port.in;

import momzzangseven.mztkbe.modules.admin.application.dto.SeedBootstrapOutcome;

/** Input port for bootstrapping seed admin accounts. */
public interface BootstrapSeedAdminsUseCase {

  SeedBootstrapOutcome execute();
}
