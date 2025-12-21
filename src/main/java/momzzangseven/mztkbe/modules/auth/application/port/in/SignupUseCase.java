package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.SignupCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.SignupResult;

public interface SignupUseCase {
  public SignupResult execute(SignupCommand command);
}
