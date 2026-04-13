package momzzangseven.mztkbe.modules.admin.application.port.in;

import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedResult;

/** Input port for the break-glass recovery reseed operation. */
public interface RecoveryReseedUseCase {

  RecoveryReseedResult execute(RecoveryReseedCommand command);
}
