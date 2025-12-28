package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.SignupCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.SignupResult;

/**
 * Input port for signup use case.
 */
public interface SignupUseCase {
    SignupResult execute(SignupCommand command);
}
